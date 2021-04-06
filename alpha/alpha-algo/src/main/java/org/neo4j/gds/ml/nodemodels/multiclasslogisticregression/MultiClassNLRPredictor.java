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
package org.neo4j.gds.ml.nodemodels.multiclasslogisticregression;

import org.neo4j.gds.embeddings.graphsage.ddl4j.ComputationContext;
import org.neo4j.gds.embeddings.graphsage.ddl4j.Variable;
import org.neo4j.gds.embeddings.graphsage.ddl4j.functions.MatrixConstant;
import org.neo4j.gds.embeddings.graphsage.ddl4j.functions.MatrixMultiplyWithTransposedSecondOperand;
import org.neo4j.gds.embeddings.graphsage.ddl4j.functions.Softmax;
import org.neo4j.gds.embeddings.graphsage.ddl4j.tensor.Matrix;
import org.neo4j.gds.ml.Predictor;
import org.neo4j.gds.ml.batch.Batch;
import org.neo4j.gds.ml.features.BiasFeature;
import org.neo4j.gds.ml.features.FeatureExtraction;
import org.neo4j.gds.ml.features.FeatureExtractor;
import org.neo4j.graphalgo.api.Graph;

import java.util.ArrayList;
import java.util.List;

import static org.neo4j.gds.ml.features.FeatureExtraction.extract;

public class MultiClassNLRPredictor implements Predictor<Matrix, MultiClassNLRData> {

    private final MultiClassNLRData modelData;

    public MultiClassNLRPredictor(MultiClassNLRData modelData) {
        this.modelData = modelData;
    }

    @Override
    public MultiClassNLRData modelData() {
        return modelData;
    }

    @Override
    public Matrix predict(Graph graph, Batch batch) {
        ComputationContext ctx = new ComputationContext();
        return ctx.forward(predictionsVariable(graph, batch));
    }

    Variable<Matrix> predictionsVariable(Graph graph, Batch batch) {
        MatrixConstant features = features(graph, batch);
        var weights = modelData.weights();
        return new Softmax(MatrixMultiplyWithTransposedSecondOperand.of(features, weights));
    }

    private MatrixConstant features(Graph graph, Batch batch) {
        var featureExtractors = featureExtractors(graph);
        return extract(batch, featureExtractors);
    }

    private List<FeatureExtractor> featureExtractors(Graph graph) {
        var featureExtractors = new ArrayList<FeatureExtractor>();
        featureExtractors.addAll(FeatureExtraction.propertyExtractors(graph, modelData().featureProperties()));
        featureExtractors.add(new BiasFeature());
        return featureExtractors;
    }
}
