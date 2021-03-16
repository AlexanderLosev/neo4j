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
package org.neo4j.graphalgo.core.utils.export.file;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.neo4j.kernel.database.DatabaseIdFactory;

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import static org.neo4j.graphalgo.core.utils.export.file.csv.CsvGraphInfoVisitor.GRAPH_INFO_FILE_NAME;

public class GraphInfoLoader {
    private final Path graphInfoPath;
    private final ObjectReader objectReader;

    public GraphInfoLoader(Path csvDirectory) {
        this.graphInfoPath = csvDirectory.resolve(GRAPH_INFO_FILE_NAME);

        CsvMapper csvMapper = new CsvMapper();
        csvMapper.enable(CsvParser.Feature.TRIM_SPACES);
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        objectReader = csvMapper.readerFor(GraphInfoLine.class).with(schema);
    }

    public GraphInfo load() {

        try (var fileReader = new FileReader(graphInfoPath.toFile(), StandardCharsets.UTF_8)) {
            var mappingIterator = objectReader.<GraphInfoLine>readValues(fileReader);

            var line = mappingIterator.next();
            var databaseId = DatabaseIdFactory.from(line.databaseName, line.databaseId);
            return ImmutableGraphInfo.builder()
                .namedDatabaseId(databaseId)
                .nodeCount(line.nodeCount)
                .build();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static class GraphInfoLine {
        @JsonProperty
        UUID databaseId;

        @JsonProperty
        String databaseName;

        @JsonProperty
        long nodeCount;
    }
}
