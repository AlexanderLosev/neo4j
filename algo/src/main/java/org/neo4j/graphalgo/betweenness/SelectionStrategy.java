/*
 * Copyright (c) 2017-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.graphalgo.betweenness;

import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.concurrency.ParallelUtil;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.core.utils.paged.PagedSimpleBitSet;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.neo4j.graphalgo.utils.StringFormatting.formatWithLocale;

public interface SelectionStrategy {

    boolean select(long nodeId);

    long size();

    enum Strategy {
        ALL,
        RANDOM,
        RANDOM_DEGREE;

        public static Strategy of(String value) {
            try {
                return Strategy.valueOf(value.toUpperCase(Locale.ENGLISH));
            } catch (IllegalArgumentException e) {
                String availableProjections = Arrays
                    .stream(Strategy.values())
                    .map(Strategy::name)
                    .collect(Collectors.joining(", "));
                throw new IllegalArgumentException(formatWithLocale(
                    "Selection strategy `%s` is not supported. Must be one of: %s.",
                    value,
                    availableProjections
                ));
            }
        }

        public static Strategy parse(Object object) {
            if (object == null) {
                return null;
            }
            if (object instanceof String) {
                return of(((String) object).toUpperCase(Locale.ENGLISH));
            }
            if (object instanceof Strategy) {
                return (Strategy) object;
            }
            return null;
        }
    }

    class All implements SelectionStrategy {

        private final long nodeCount;

        public All(long nodeCount) {
            this.nodeCount = nodeCount;
        }

        @Override
        public boolean select(long nodeId) {
            return true;
        }

        @Override
        public long size() {
            return nodeCount;
        }
    }

    class Random implements SelectionStrategy {

        private final PagedSimpleBitSet bitSet;
        private final long size;

        public Random(Graph graph, double probability, AllocationTracker tracker) {
            this.bitSet = PagedSimpleBitSet.newBitSet(graph.nodeCount(), tracker);
            final SecureRandom random = new SecureRandom();
            for (int i = 0; i < graph.nodeCount(); i++) {
                if (random.nextDouble() < probability) {
                    this.bitSet.put(i);
                }
            }
            this.size = this.bitSet.size();
        }

        @Override
        public boolean select(long nodeId) {
            return bitSet.contains(nodeId);
        }

        @Override
        public long size() {
            return size;
        }

    }

    class RandomDegree implements SelectionStrategy {

        private final double maxDegree;
        // TODO: benchmark and potentially replace with hppc BitSet
        private final PagedSimpleBitSet bitSet;
        private final long size;

        public RandomDegree(
            Graph graph,
            double probabilityOffset,
            ExecutorService executorService,
            int concurrency,
            AllocationTracker tracker
        ) {
            this.bitSet = PagedSimpleBitSet.newBitSet(graph.nodeCount(), tracker);
            this.maxDegree = getMaxDegree(graph, executorService, concurrency);

            SecureRandom random = new SecureRandom();
            ParallelUtil.readParallel(concurrency, graph.nodeCount(), executorService, (from, to) -> {
                for (long nodeId = from; nodeId < to; nodeId++) {
                    if (random.nextDouble() - probabilityOffset <= graph.degree(nodeId) / maxDegree) {
                        bitSet.put(nodeId);
                    }
                }
            });

            this.size = bitSet.size();
        }

        @Override
        public boolean select(long nodeId) {
            return bitSet.contains(nodeId);
        }

        @Override
        public long size() {
            return size;
        }

        private long getMaxDegree(Graph graph, ExecutorService executorService, int concurrency) {
            AtomicInteger mx = new AtomicInteger(0);
            ParallelUtil.readParallel(concurrency, graph.nodeCount(), executorService, (from, to) -> {
                for (long nodeId = from; nodeId < to; nodeId++) {
                    int degree = graph.degree(nodeId);
                    int current;
                    do {
                        current = mx.get();
                    } while (degree > current && !mx.compareAndSet(current, degree));
                }
            });
            return mx.get();
        }
    }
}