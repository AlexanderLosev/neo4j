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
package org.neo4j.graphalgo.core.loading;

import org.neo4j.graphalgo.NodeLabel;
import org.neo4j.graphalgo.api.NodeMapping;
import org.neo4j.graphalgo.core.utils.mem.AllocationTracker;
import org.neo4j.graphalgo.core.utils.paged.HugeAtomicBitSet;

import java.util.Map;

public interface NodeMappingBuilder<BUILDER extends InternalIdMappingBuilder<? extends IdMappingAllocator>> {

    NodeMapping build(
        BUILDER idMapBuilder,
        Map<NodeLabel, HugeAtomicBitSet> labelInformation,
        long highestNodeId,
        int concurrency,
        AllocationTracker tracker
    );

    default Capturing capture(BUILDER idMapBuilder) {
        return ((labelInformation, highestNodeId, concurrency, tracker) -> this.build(
            idMapBuilder,
            labelInformation,
            highestNodeId,
            concurrency,
            tracker
        ));
    }

    interface Capturing {

        NodeMapping build(
            Map<NodeLabel, HugeAtomicBitSet> labelInformation,
            long highestNodeId,
            int concurrency,
            AllocationTracker tracker
        );
    }

}
