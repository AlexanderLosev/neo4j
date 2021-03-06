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

import com.carrotsearch.hppc.ObjectLongMap;
import org.jetbrains.annotations.NotNull;
import org.neo4j.graphalgo.NodeProjections;
import org.neo4j.graphalgo.Orientation;
import org.neo4j.graphalgo.RelationshipProjections;
import org.neo4j.graphalgo.RelationshipType;
import org.neo4j.graphalgo.api.CSRGraphStoreFactory;
import org.neo4j.graphalgo.api.GraphLoaderContext;
import org.neo4j.graphalgo.config.GraphCreateFromStoreConfig;
import org.neo4j.graphalgo.core.GraphDimensions;
import org.neo4j.graphalgo.core.GraphDimensionsStoreReader;
import org.neo4j.graphalgo.core.huge.HugeGraph;
import org.neo4j.graphalgo.core.huge.TransientAdjacencyDegrees;
import org.neo4j.graphalgo.core.huge.TransientAdjacencyList;
import org.neo4j.graphalgo.core.huge.TransientAdjacencyOffsets;
import org.neo4j.graphalgo.core.loading.nodeproperties.NodePropertiesFromStoreBuilder;
import org.neo4j.graphalgo.core.utils.BatchingProgressLogger;
import org.neo4j.graphalgo.core.utils.ProgressLogger;
import org.neo4j.graphalgo.core.utils.mem.AllocationTracker;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimation;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimations;
import org.neo4j.graphalgo.core.utils.progress.EmptyProgressEventTracker;

import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.toMap;
import static org.neo4j.graphalgo.core.GraphDimensionsValidation.validate;
import static org.neo4j.graphalgo.utils.StringFormatting.formatWithLocale;

public final class NativeFactory extends CSRGraphStoreFactory<GraphCreateFromStoreConfig> {

    private final GraphCreateFromStoreConfig storeConfig;

    public NativeFactory(
        GraphCreateFromStoreConfig graphCreateConfig,
        GraphLoaderContext loadingContext
    ) {
        this(
            graphCreateConfig,
            loadingContext,
            new GraphDimensionsStoreReader(loadingContext.transaction(), graphCreateConfig).call()
        );
    }

    public NativeFactory(
        GraphCreateFromStoreConfig graphCreateConfig,
        GraphLoaderContext loadingContext,
        GraphDimensions graphDimensions
    ) {
        super(graphCreateConfig, loadingContext, graphDimensions);
        this.storeConfig = graphCreateConfig;
    }

    @Override
    public MemoryEstimation memoryEstimation() {
        return getMemoryEstimation(storeConfig.nodeProjections(), storeConfig.relationshipProjections());
    }

    public static MemoryEstimation getMemoryEstimation(
        NodeProjections nodeProjections,
        RelationshipProjections relationshipProjections
    ) {
        MemoryEstimations.Builder builder = MemoryEstimations.builder(HugeGraph.class);

        // node information
        if (IdMapImplementations.useBitIdMap()) {
            builder.add("nodeIdMap", BitIdMap.memoryEstimation());
        } else {
            builder.add("nodeIdMap", IdMap.memoryEstimation());
        }

        // nodeProperties
        nodeProjections.allProperties()
            .forEach(property -> builder.add(property, NodePropertiesFromStoreBuilder.memoryEstimation()));

        // relationships
        relationshipProjections.projections().forEach((relationshipType, relationshipProjection) -> {

            boolean undirected = relationshipProjection.orientation() == Orientation.UNDIRECTED;

            // adjacency list
            builder.add(
                formatWithLocale("adjacency degrees for '%s'", relationshipType),
                TransientAdjacencyDegrees.memoryEstimation()
            );
            builder.add(
                formatWithLocale("adjacency list for '%s'", relationshipType),
                TransientAdjacencyList.compressedMemoryEstimation(relationshipType, undirected)
            );
            builder.add(
                formatWithLocale("adjacency offsets for '%s'", relationshipType),
                TransientAdjacencyOffsets.memoryEstimation()
            );
            // all properties per projection
            relationshipProjection.properties().mappings().forEach(resolvedPropertyMapping -> {
                builder.add(
                    formatWithLocale("property '%s.%s", relationshipType, resolvedPropertyMapping.propertyKey()),
                    TransientAdjacencyList.uncompressedMemoryEstimation(relationshipType, undirected)
                );
                builder.add(
                    formatWithLocale("property offset '%s.%s", relationshipType, resolvedPropertyMapping.propertyKey()),
                    TransientAdjacencyOffsets.memoryEstimation()
                );
            });
        });

        return builder.build();
    }

    @Override
    protected ProgressLogger initProgressLogger() {
        long relationshipCount = graphCreateConfig
            .relationshipProjections()
            .projections()
            .entrySet()
            .stream()
            .map(entry -> {
                Long relCount = entry.getKey().name.equals("*")
                    ? dimensions.relationshipCounts().values().stream().reduce(Long::sum).orElse(0L)
                    : dimensions.relationshipCounts().getOrDefault(entry.getKey(), 0L);

                return entry.getValue().orientation() == Orientation.UNDIRECTED
                    ? relCount * 2
                    : relCount;
            }).mapToLong(Long::longValue).sum();

        return new BatchingProgressLogger(
            loadingContext.log(),
            dimensions.nodeCount() + relationshipCount,
            TASK_LOADING,
            graphCreateConfig.readConcurrency(),
            // TODO: actual tracker
            EmptyProgressEventTracker.INSTANCE
        );
    }

    @Override
    public ImportResult<CSRGraphStore> build() {
        validate(dimensions, storeConfig);

        int concurrency = graphCreateConfig.readConcurrency();
        AllocationTracker tracker = loadingContext.tracker();
        IdsAndProperties nodes = loadNodes(concurrency);
        RelationshipImportResult relationships = loadRelationships(tracker, nodes, concurrency);
        CSRGraphStore graphStore = createGraphStore(nodes, relationships, tracker, dimensions);

        logLoadingSummary(graphStore, Optional.of(tracker));

        return ImportResult.of(dimensions, graphStore);
    }

    private IdsAndProperties loadNodes(int concurrency) {
        var properties = IndexPropertyMappings.prepareProperties(
            graphCreateConfig,
            dimensions,
            loadingContext.transaction()
        );

        var scanningNodesImporter = IdMapImplementations.useBitIdMap()
            ? new ScanningNodesImporter<>(graphCreateConfig, loadingContext, dimensions, progressLogger, concurrency, properties, bitIdMappingBuilderFactory(), IdMapImplementations.bitIdMapBuilder())
            : new ScanningNodesImporter<>(graphCreateConfig, loadingContext, dimensions, progressLogger, concurrency, properties, hugeIdMappingBuilderFactory(), IdMapImplementations.hugeIdMapBuilder());

        return scanningNodesImporter.call(loadingContext.log());
    }

    @NotNull
    private InternalIdMappingBuilderFactory<InternalBitIdMappingBuilder, InternalBitIdMappingBuilder.BulkAdder> bitIdMappingBuilderFactory() {
        return dimensions -> InternalBitIdMappingBuilder.of(dimensions.highestNeoId() + 1, loadingContext.tracker());
    }

    @NotNull
    private InternalIdMappingBuilderFactory<InternalHugeIdMappingBuilder, InternalHugeIdMappingBuilder.BulkAdder> hugeIdMappingBuilderFactory() {
        return dimensions -> InternalHugeIdMappingBuilder.of(dimensions.nodeCount(), loadingContext.tracker());
    }

    private RelationshipImportResult loadRelationships(
        AllocationTracker tracker,
        IdsAndProperties idsAndProperties,
        int concurrency
    ) {
        Map<RelationshipType, AdjacencyListWithPropertiesBuilder> allBuilders = graphCreateConfig
            .relationshipProjections()
            .projections()
            .entrySet()
            .stream()
            .collect(toMap(
                Map.Entry::getKey,
                projectionEntry -> AdjacencyListWithPropertiesBuilder.create(
                    dimensions.nodeCount(),
                    projectionEntry.getValue(),
                    dimensions.relationshipPropertyTokens(),
                    TransientAdjacencyListBuilder.builderFactory(tracker),
                    TransientAdjacencyDegrees.Factory.INSTANCE,
                    TransientAdjacencyOffsets.Factory.INSTANCE,
                    tracker
                )
            ));

        ObjectLongMap<RelationshipType> relationshipCounts = new ScanningRelationshipsImporter(
            graphCreateConfig,
            loadingContext,
            dimensions,
            progressLogger,
            idsAndProperties.idMap(),
            allBuilders,
            concurrency
        ).call(loadingContext.log());

        return RelationshipImportResult.of(allBuilders, relationshipCounts, dimensions);
    }
}
