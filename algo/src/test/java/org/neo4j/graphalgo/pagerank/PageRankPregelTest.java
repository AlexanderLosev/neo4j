/*
 * Copyright (c) "Neo4j"
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
package org.neo4j.graphalgo.pagerank;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.utils.ProgressLogger;
import org.neo4j.graphalgo.core.utils.mem.AllocationTracker;
import org.neo4j.graphalgo.core.utils.progress.EmptyProgressEventTracker;
import org.neo4j.graphalgo.extension.GdlExtension;
import org.neo4j.graphalgo.extension.GdlGraph;
import org.neo4j.graphalgo.extension.Inject;
import org.neo4j.graphalgo.extension.TestGraph;
import org.neo4j.graphalgo.pagerank.PageRankPregelAlgorithmFactory.Mode;
import org.neo4j.logging.NullLog;

import java.util.Arrays;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@GdlExtension
class PageRankPregelTest {

    private static final double SCORE_PRECISION = 1E-5;

    @Nested
    class WikiGraph {

        // https://en.wikipedia.org/wiki/PageRank#/media/File:PageRanks-Example.jpg
        @GdlGraph
        private static final String DB_CYPHER =
            "CREATE" +
            "  (a:Node { expectedRank: 0.3040965, expectedPersonalizedRank1: 0.17053529152163158 , expectedPersonalizedRank2: 0.017454997930076894 })" +
            ", (b:Node { expectedRank: 3.5604297, expectedPersonalizedRank1: 0.3216114449911402  , expectedPersonalizedRank2: 0.813246950528992    })" +
            ", (c:Node { expectedRank: 3.1757906, expectedPersonalizedRank1: 0.27329311398643763 , expectedPersonalizedRank2: 0.690991752640184    })" +
            ", (d:Node { expectedRank: 0.3625935, expectedPersonalizedRank1: 0.048318333106500536, expectedPersonalizedRank2: 0.041070583050331164 })" +
            ", (e:Node { expectedRank: 0.7503465, expectedPersonalizedRank1: 0.17053529152163158 , expectedPersonalizedRank2: 0.1449550029964717   })" +
            ", (f:Node { expectedRank: 0.3625935, expectedPersonalizedRank1: 0.048318333106500536, expectedPersonalizedRank2: 0.041070583050331164 })" +
            ", (g:Node { expectedRank: 0.15     , expectedPersonalizedRank1: 0.0                 , expectedPersonalizedRank2: 0.0                  })" +
            ", (h:Node { expectedRank: 0.15     , expectedPersonalizedRank1: 0.0                 , expectedPersonalizedRank2: 0.0                  })" +
            ", (i:Node { expectedRank: 0.15     , expectedPersonalizedRank1: 0.0                 , expectedPersonalizedRank2: 0.0                  })" +
            ", (j:Node { expectedRank: 0.15     , expectedPersonalizedRank1: 0.0                 , expectedPersonalizedRank2: 0.0                  })" +
            ", (k:Node { expectedRank: 0.15     , expectedPersonalizedRank1: 0.0                 , expectedPersonalizedRank2: 0.15000000000000002  })" +
            ", (b)-[:TYPE]->(c)" +
            ", (c)-[:TYPE]->(b)" +
            ", (d)-[:TYPE]->(a)" +
            ", (d)-[:TYPE]->(b)" +
            ", (e)-[:TYPE]->(b)" +
            ", (e)-[:TYPE]->(d)" +
            ", (e)-[:TYPE]->(f)" +
            ", (f)-[:TYPE]->(b)" +
            ", (f)-[:TYPE]->(e)" +
            ", (g)-[:TYPE]->(b)" +
            ", (g)-[:TYPE]->(e)" +
            ", (h)-[:TYPE]->(b)" +
            ", (h)-[:TYPE]->(e)" +
            ", (i)-[:TYPE]->(b)" +
            ", (i)-[:TYPE]->(e)" +
            ", (j)-[:TYPE]->(e)" +
            ", (k)-[:TYPE]->(e)";

        @Inject
        private TestGraph graph;

        @Test
        void withoutTolerance() {
            var config = ImmutablePageRankStreamConfig.builder()
                .maxIterations(40)
                .concurrency(1)
                .tolerance(0)
                .build();

            var actualGds = runOnGds(graph, config).result().asNodeProperties();
            var actualPregel = runOnPregel(graph, config)
                .scores()
                .asNodeProperties();

            var expected = graph.nodeProperties("expectedRank");

            for (int nodeId = 0; nodeId < graph.nodeCount(); nodeId++) {
                assertThat(actualGds.doubleValue(nodeId)).isEqualTo(expected.doubleValue(nodeId), within(SCORE_PRECISION));
                assertThat(actualPregel.doubleValue(nodeId)).isEqualTo(expected.doubleValue(nodeId), within(SCORE_PRECISION));
            }
        }

        @ParameterizedTest
        @CsvSource(value = {"0.5, 2", "0.1, 13"})
        void withTolerance(double tolerance, int expectedIterations) {
            var config = ImmutablePageRankStreamConfig.builder()
                .maxIterations(40)
                .concurrency(1)
                .tolerance(tolerance)
                .build();

            var pregelResult = runOnPregel(graph, config);

            // initial iteration is counted extra in Pregel
            assertThat(pregelResult.iterations()).isEqualTo(expectedIterations);
        }

        @ParameterizedTest
        @CsvSource(value = {
            "a;e,expectedPersonalizedRank1",
            "k;b,expectedPersonalizedRank2"
        })
        void withSourceNodes(String sourceNodesString, String expectedPropertyKey) {
            // ids are converted to mapped ids within the algorithms
            var sourceNodeIds = Arrays.stream(sourceNodesString.split(";")).mapToLong(graph::toOriginalNodeId).toArray();

            var config = ImmutablePageRankStreamConfig.builder()
                .maxIterations(40)
                .tolerance(0)
                .concurrency(1)
                .build();

            var actualGds = runOnGds(graph, config, sourceNodeIds).result().asNodeProperties();
            var actualPregel = runOnPregel(graph, config, sourceNodeIds, Mode.DEFAULT)
                .scores()
                .asNodeProperties();

            var expected = graph.nodeProperties(expectedPropertyKey);

            for (int nodeId = 0; nodeId < graph.nodeCount(); nodeId++) {
                assertThat(actualGds.doubleValue(nodeId)).isEqualTo(expected.doubleValue(nodeId), within(SCORE_PRECISION));
                assertThat(actualPregel.doubleValue(nodeId)).isEqualTo(expected.doubleValue(nodeId), within(SCORE_PRECISION));
            }
        }
    }

    @Nested
    class WeightedWikiTest {
        // https://en.wikipedia.org/wiki/PageRank#/media/File:PageRanks-Example.jpg
        @GdlGraph
        private static final String DB_CYPHER =
            "CREATE" +
            "  (a:Node { expectedRank: 0.24919 })" +
            ", (b:Node { expectedRank: 3.69822 })" +
            ", (c:Node { expectedRank: 3.29307 })" +
            ", (d:Node { expectedRank: 0.58349 })" +
            ", (e:Node { expectedRank: 0.72855 })" +
            ", (f:Node { expectedRank: 0.27385 })" +
            ", (g:Node { expectedRank: 0.15 })" +
            ", (h:Node { expectedRank: 0.15 })" +
            ", (i:Node { expectedRank: 0.15 })" +
            ", (j:Node { expectedRank: 0.15 })" +
            ", (k:Node { expectedRank: 0.15 })" +
            ", (b)-[:TYPE { weight: 1.0,   unnormalizedWeight: 5.0 }]->(c)" +
            ", (c)-[:TYPE { weight: 1.0,   unnormalizedWeight: 10.0 }]->(b)" +
            ", (d)-[:TYPE { weight: 0.2,   unnormalizedWeight: 2.0 }]->(a)" +
            ", (d)-[:TYPE { weight: 0.8,   unnormalizedWeight: 8.0 }]->(b)" +
            ", (e)-[:TYPE { weight: 0.10,  unnormalizedWeight: 1.0 }]->(b)" +
            ", (e)-[:TYPE { weight: 0.70,  unnormalizedWeight: 7.0 }]->(d)" +
            ", (e)-[:TYPE { weight: 0.20,  unnormalizedWeight: 2.0 }]->(f)" +
            ", (f)-[:TYPE { weight: 0.7,   unnormalizedWeight: 7.0 }]->(b)" +
            ", (f)-[:TYPE { weight: 0.3,   unnormalizedWeight: 3.0 }]->(e)" +
            ", (g)-[:TYPE { weight: 0.01,  unnormalizedWeight: 0.1 }]->(b)" +
            ", (g)-[:TYPE { weight: 0.99,  unnormalizedWeight: 9.9 }]->(e)" +
            ", (h)-[:TYPE { weight: 0.5,   unnormalizedWeight: 5.0 }]->(b)" +
            ", (h)-[:TYPE { weight: 0.5,   unnormalizedWeight: 5.0 }]->(e)" +
            ", (i)-[:TYPE { weight: 0.5,   unnormalizedWeight: 5.0 }]->(b)" +
            ", (i)-[:TYPE { weight: 0.5,   unnormalizedWeight: 5.0 }]->(e)" +
            ", (j)-[:TYPE { weight: 1.0,   unnormalizedWeight: 10.0 }]->(e)" +
            ", (k)-[:TYPE { weight: 1.0,   unnormalizedWeight: 10.0 }]->(e)";

        @Inject
        private Graph graph;

        @ParameterizedTest
        @ValueSource(strings = {"weight", "unnormalizedWeight"})
        void withWeights(String relationshipWeight) {
            var config = ImmutablePageRankStreamConfig.builder()
                .maxIterations(40)
                .tolerance(0)
                .relationshipWeightProperty(relationshipWeight)
                .concurrency(1)
                .build();

            var actualGds = runOnGds(graph, config).result().asNodeProperties();
            var actualPregel = runOnPregel(graph, config)
                .scores()
                .asNodeProperties();

            var expected = graph.nodeProperties("expectedRank");

            for (int nodeId = 0; nodeId < graph.nodeCount(); nodeId++) {
                assertThat(actualGds.doubleValue(nodeId)).isEqualTo(expected.doubleValue(nodeId), within(SCORE_PRECISION));
                assertThat(actualPregel.doubleValue(nodeId)).isEqualTo(expected.doubleValue(nodeId), within(SCORE_PRECISION));
            }
        }
    }

    @Nested
    class ArticleRankGraph {

        @GdlGraph
        private static final String DB_CYPHER =
            "CREATE" +
            "  (a:Label1 { expectedRank: 0.19991328 })" +
            ", (b:Label1 { expectedRank: 0.41704274 })" +
            ", (c:Label1 { expectedRank: 0.31791456 })" +
            ", (d:Label1 { expectedRank: 0.18921376 })" +
            ", (e:Label1 { expectedRank: 0.19991328 })" +
            ", (f:Label1 { expectedRank: 0.18921376 })" +
            ", (g:Label1 { expectedRank: 0.15 })" +
            ", (h:Label1 { expectedRank: 0.15 })" +
            ", (i:Label1 { expectedRank: 0.15 })" +
            ", (j:Label1 { expectedRank: 0.15 })" +
            ", (b)-[:TYPE1]->(c)" +
            ", (c)-[:TYPE1]->(b)" +
            ", (d)-[:TYPE1]->(a)" +
            ", (d)-[:TYPE1]->(b)" +
            ", (e)-[:TYPE1]->(b)" +
            ", (e)-[:TYPE1]->(d)" +
            ", (e)-[:TYPE1]->(f)" +
            ", (f)-[:TYPE1]->(b)" +
            ", (f)-[:TYPE1]->(e)";

        @Inject
        private Graph graph;

        @Test
        void articleRank() {
            var config = ImmutablePageRankStreamConfig
                .builder()
                .maxIterations(40)
                .tolerance(0)
                .concurrency(1)
                .build();

            var actual = runOnPregel(graph, config, new long[0], Mode.ARTICLE_RANK)
                .scores()
                .asNodeProperties();

            var expected = graph.nodeProperties("expectedRank");

            for (int nodeId = 0; nodeId < graph.nodeCount(); nodeId++) {
                assertThat(actual.doubleValue(nodeId)).isEqualTo(expected.doubleValue(nodeId), within(1e-6));
            }
        }
    }

    PageRank runOnGds(Graph graph, PageRankBaseConfig config) {
        return runOnGds(graph, config, new long[0]);
    }

    PageRank runOnGds(Graph graph, PageRankBaseConfig config, long[] sourceNodeIds) {
        var algorithmType = config.relationshipWeightProperty() != null
            ? PageRankAlgorithmType.WEIGHTED
            : PageRankAlgorithmType.NON_WEIGHTED;
        return algorithmType
            .create(graph, config, LongStream.of(sourceNodeIds), ProgressLogger.NULL_LOGGER, AllocationTracker.empty())
            .compute();
    }

    PageRankPregelResult runOnPregel(Graph graph, PageRankBaseConfig config) {
        return runOnPregel(graph, config, new long[0], Mode.DEFAULT);
    }

    PageRankPregelResult runOnPregel(Graph graph, PageRankBaseConfig config, long[] sourceNodeIds, Mode mode) {
        var configBuilder = ImmutablePageRankPregelConfig.builder()
            .maxIterations(config.maxIterations() + 1)
            .dampingFactor(config.dampingFactor())
            .concurrency(config.concurrency())
            .relationshipWeightProperty(config.relationshipWeightProperty())
            .sourceNodeIds(LongStream.of(sourceNodeIds))
            .tolerance(config.tolerance())
            .isAsynchronous(false);

        return new PageRankPregelAlgorithmFactory<>(mode)
            .build(
                graph,
                configBuilder.build(),
                AllocationTracker.empty(),
                NullLog.getInstance(),
                EmptyProgressEventTracker.INSTANCE
            )
            .compute();
    }
}
