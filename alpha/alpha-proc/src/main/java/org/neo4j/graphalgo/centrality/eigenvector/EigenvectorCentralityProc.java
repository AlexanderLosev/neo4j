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
package org.neo4j.graphalgo.centrality.eigenvector;

import org.neo4j.gds.scaling.ScalarScaler;
import org.neo4j.graphalgo.AlgoBaseProc;
import org.neo4j.graphalgo.AlgorithmFactory;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.config.GraphCreateConfig;
import org.neo4j.graphalgo.core.CypherMapWrapper;
import org.neo4j.graphalgo.core.concurrency.Pools;
import org.neo4j.graphalgo.core.utils.ProgressTimer;
import org.neo4j.graphalgo.core.write.NodePropertyExporter;
import org.neo4j.graphalgo.pagerank.PageRank;
import org.neo4j.graphalgo.result.AbstractCentralityResultBuilder;
import org.neo4j.graphalgo.result.CentralityResult;
import org.neo4j.graphalgo.results.CentralityScore;
import org.neo4j.graphalgo.results.NormalizedCentralityResult;
import org.neo4j.graphalgo.results.PageRankScore;
import org.neo4j.graphalgo.utils.CentralityUtils;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.neo4j.procedure.Mode.READ;
import static org.neo4j.procedure.Mode.WRITE;

public final class EigenvectorCentralityProc extends AlgoBaseProc<PageRank, PageRank, EigenvectorCentralityConfig> {
    private static final String DESCRIPTION = "Eigenvector Centrality measures the transitive influence or connectivity of nodes.";

    @Procedure(value = "gds.alpha.eigenvector.write", mode = WRITE)
    @Description(DESCRIPTION)
    public Stream<PageRankScore.Stats> write(
        @Name(value = "graphName") Object graphNameOrConfig,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        ComputationResult<PageRank, PageRank, EigenvectorCentralityConfig> computationResult = compute(
            graphNameOrConfig,
            configuration
        );
        PageRank algorithm = computationResult.algorithm();
        Graph graph = computationResult.graph();

        EigenvectorCentralityConfig config = computationResult.config();
        CentralityResult normalizedResults = normalize(config.normalization(), algorithm.result(), computationResult.config().concurrency());

        AbstractCentralityResultBuilder<PageRankScore.Stats> statsBuilder = new PageRankScore.Stats.Builder(callContext, config.concurrency())
            .withIterations(algorithm.iterations())
            .withDampingFactor(algorithm.dampingFactor());

        statsBuilder
            .withConfig(config)
            .withCreateMillis(computationResult.createMillis())
            .withComputeMillis(computationResult.computeMillis())
            .withNodeCount(graph.nodeCount());

        if (graph.isEmpty()) {
            graph.release();
            return Stream.of(statsBuilder.build());
        }

        statsBuilder.withCentralityFunction(normalizedResults::score);

        // NOTE: could not use `writeNodeProperties` just yet, as this requires changes to
        //  the Page Rank class and therefore to all product Page Rank procs as well.
        try(ProgressTimer ignore = ProgressTimer.start(statsBuilder::withWriteMillis)) {
            NodePropertyExporter exporter = NodePropertyExporter
                .builder(api, computationResult.graph(), algorithm.getTerminationFlag())
                .withLog(log)
                .parallel(Pools.DEFAULT, config.writeConcurrency())
                .build();
            normalizedResults.export(config.writeProperty(), exporter);
        }

        graph.release();
        return Stream.of(statsBuilder.build());
    }

    @Procedure(name = "gds.alpha.eigenvector.stream", mode = READ)
    @Description(DESCRIPTION)
    public Stream<CentralityScore> stream(
        @Name(value = "graphName") Object graphNameOrConfig,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        var computationResult = compute(
            graphNameOrConfig,
            configuration
        );
        var result = computationResult.result();
        var config = computationResult.config();

        String scalerName = computationResult.config().normalization();
        var normalizedResult = normalize(scalerName, result.result(), config.concurrency());
        return CentralityUtils.streamResults(computationResult.graph(), normalizedResult);
    }


    @Override
    protected AlgorithmFactory<PageRank, EigenvectorCentralityConfig> algorithmFactory() {
        return new EigenvectorCentralityAlgorithmFactory();
    }

    @Override
    protected EigenvectorCentralityConfig newConfig(
        String username,
        Optional<String> graphName,
        Optional<GraphCreateConfig> maybeImplicitCreate,
        CypherMapWrapper config
    ) {
        return EigenvectorCentralityConfig.of(username, graphName, maybeImplicitCreate, config);
    }

    private CentralityResult normalize(String scalerName, CentralityResult stats, int concurrency) {
        var scaler = ScalarScaler.Variant
            .lookup(scalerName)
            .create(stats.asNodeProperties(), stats.array().size(), concurrency, Pools.DEFAULT);
        return new NormalizedCentralityResult(stats, scaler);
    }


}
