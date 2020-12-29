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
package org.neo4j.gds.ml.nodemodels.multiclasslogisticregression;

import org.neo4j.gds.embeddings.graphsage.ddl4j.ComputationContext;
import org.neo4j.gds.embeddings.graphsage.ddl4j.tensor.Matrix;
import org.neo4j.gds.ml.Batch;
import org.neo4j.gds.ml.Predictor;
import org.neo4j.graphalgo.api.Graph;

public class MultiClassNodeLogisticRegressionPredictor extends
             MultiClassNodeLogisticRegressionBase implements
    Predictor<ClassProbabilities, MultiClassNodeLogisticRegressionData> {

    MultiClassNodeLogisticRegressionPredictor(MultiClassNodeLogisticRegressionData modelData) {
        super(modelData);
    }

    @Override
    public MultiClassNodeLogisticRegressionData modelData() {
        return modelData;
    }

    @Override
    public ClassProbabilities predict(Graph graph, Batch batch) {
        ComputationContext ctx = new ComputationContext();
        Matrix forward = ctx.forward(predictions(features(graph, batch), modelData.weights()));
        return new ClassProbabilities(forward, modelData.classIdMap());
    }
}