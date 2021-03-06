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

import org.neo4j.graphalgo.api.AdjacencyOffsets;
import org.neo4j.graphalgo.core.loading.AdjacencyOffsetsFactory;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimation;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimations;
import org.neo4j.graphalgo.core.utils.paged.HugeLongArray;

public class TransientAdjacencyOffsets implements AdjacencyOffsets {

    public enum Factory implements AdjacencyOffsetsFactory {
        INSTANCE;

        @Override
        public AdjacencyOffsets newOffsets(HugeLongArray offsets) {
            return new TransientAdjacencyOffsets(offsets);
        }
    }

    private final HugeLongArray offsets;

    public TransientAdjacencyOffsets(HugeLongArray offsets) {this.offsets = offsets;}

    public static MemoryEstimation memoryEstimation() {
        return MemoryEstimations
            .builder(TransientAdjacencyOffsets.class)
            .perNode("offsets", HugeLongArray::memoryEstimation)
            .build();
    }

    @Override
    public long get(long index) {
        return offsets.get(index);
    }

    @Override
    public void close() {
    }
}
