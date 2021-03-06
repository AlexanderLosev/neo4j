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
package org.neo4j.graphalgo.core.huge;

import org.neo4j.graphalgo.api.AdjacencyDegrees;
import org.neo4j.graphalgo.core.loading.AdjacencyDegreesFactory;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimation;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimations;
import org.neo4j.graphalgo.core.utils.paged.HugeIntArray;

public final class TransientAdjacencyDegrees implements AdjacencyDegrees {

    public enum Factory implements AdjacencyDegreesFactory {
        INSTANCE;

        @Override
        public AdjacencyDegrees newDegrees(HugeIntArray degrees) {
            return new TransientAdjacencyDegrees(degrees);
        }
    }

    private final HugeIntArray degrees;

    private TransientAdjacencyDegrees(HugeIntArray degrees) {
        this.degrees = degrees;
    }

    @Override
    public int degree(long node) {
        return degrees.get(node);
    }

    @Override
    public void close() {
    }

    public static MemoryEstimation memoryEstimation() {
        return MemoryEstimations
            .builder(TransientAdjacencyDegrees.class)
            .perNode("degrees", HugeIntArray::memoryEstimation)
            .build();
    }
}
