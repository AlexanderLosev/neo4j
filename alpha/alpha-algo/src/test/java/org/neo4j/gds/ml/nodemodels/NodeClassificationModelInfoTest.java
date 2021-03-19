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
package org.neo4j.gds.ml.nodemodels;

import org.junit.jupiter.api.Test;
import org.neo4j.gds.ml.nodemodels.metrics.AllClassMetric;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NodeClassificationModelInfoTest {

    @Test
    void shouldCreateMap() {
        var info = NodeClassificationModelInfo.of(
            List.of(),
            Map.of(),
            Map.of()
        );
        var expected = Map.of(
            "bestParameters", Map.of(),
            "classes", List.of(),
            "metrics", Map.of()
        );
        assertThat(info.toMap()).containsExactlyInAnyOrderEntriesOf(expected);
    }

    @Test
    void shouldCreateMapWithStats() {
        Map<String, Object> params = Map.of("myParameter", "value");
        var trainStats = ImmutableModelStats.of(params, 0.5, 0.0, 1.0);
        var validationStats = ImmutableModelStats.of(params, 0.4, 0.0, 0.8);
        var metricData = MetricData.of(List.of(trainStats), List.of(validationStats), 4.0, 4.1);
        var info = NodeClassificationModelInfo.of(
            List.of(42L, Long.MAX_VALUE),
            params,
            Map.of(AllClassMetric.F1_WEIGHTED, metricData)
        );
        var expected = Map.of(
            "bestParameters", params,
            "classes", List.of(42, Long.MAX_VALUE),
            "metrics", Map.of(
                "F1_WEIGHTED", Map.of(
                    "outerTrain", 4.0,
                    "test", 4.1,
                    "train", List.of(Map.of(
                        "avg", 0.5,
                        "max", 1.0,
                        "min", 0.0,
                        "params", Map.of("myParameter", "value")
                    )),
                    "validation", List.of(Map.of(
                        "avg", 0.4,
                        "max", 0.8,
                        "min", 0.0,
                        "params", Map.of("myParameter", "value")
                    ))
                )
            )
        );
        assertThat(info.toMap()).containsExactlyInAnyOrderEntriesOf(expected);
    }

}
