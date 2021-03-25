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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.util.StdConverter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.neo4j.graphalgo.RelationshipType;
import org.neo4j.graphalgo.api.DefaultValue;
import org.neo4j.graphalgo.api.GraphStore;
import org.neo4j.graphalgo.api.nodeproperties.ValueType;
import org.neo4j.graphalgo.api.schema.RelationshipSchema;
import org.neo4j.graphalgo.core.Aggregation;
import org.neo4j.graphalgo.core.utils.export.file.schema.RelationshipSchemaBuilderVisitor;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;

import static org.neo4j.graphalgo.core.utils.export.file.csv.CsvRelationshipSchemaVisitor.RELATIONSHIP_SCHEMA_FILE_NAME;

public class RelationshipSchemaLoader {

    private final ObjectReader objectReader;
    private final Path relationshipSchemaPath;

    RelationshipSchemaLoader(Path csvDirectory) {
        this.relationshipSchemaPath = csvDirectory.resolve(RELATIONSHIP_SCHEMA_FILE_NAME);
        CsvMapper csvMapper = new CsvMapper();
        csvMapper.enable(CsvParser.Feature.TRIM_SPACES);
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        objectReader = csvMapper.readerFor(SchemaLine.class).with(schema);
    }

    RelationshipSchema load() {
        var schemaBuilder = new RelationshipSchemaBuilderVisitor();

        try(var reader = new BufferedReader(new FileReader(relationshipSchemaPath.toFile(), StandardCharsets.UTF_8))) {
            var linesIterator = objectReader.<SchemaLine> readValues(reader);
            while(linesIterator.hasNext()) {
                var schemaLine = linesIterator.next();
                schemaBuilder.relationshipType(schemaLine.relationshipType);
                schemaBuilder.key(schemaLine.propertyKey);
                schemaBuilder.valueType(schemaLine.valueType);
                schemaBuilder.defaultValue(schemaLine.defaultValue);
                schemaBuilder.state(schemaLine.state);
                schemaBuilder.aggregation(schemaLine.aggregation);
                schemaBuilder.endOfEntity();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        schemaBuilder.close();
        return schemaBuilder.schema();
    }

    public static class SchemaLine {

        @JsonProperty
        @JsonDeserialize(converter = RelationshipTypeConverter.class)
        RelationshipType relationshipType;

        @JsonProperty
        String propertyKey;

        @JsonProperty
        @JsonDeserialize(converter = ValueTypeConverter.class)
        ValueType valueType;

        @JsonProperty
        @JsonDeserialize(converter = DefaultValueConverter.class)
        DefaultValue defaultValue;

        @JsonProperty
        Aggregation aggregation;

        @JsonProperty
        @JsonDeserialize(converter = PropertyStateConverter.class)
        GraphStore.PropertyState state;
    }

    private static class RelationshipTypeConverter extends StdConverter<String, RelationshipType> {
        @Override
        public RelationshipType convert(String value) {
            return RelationshipType.of(value);
        }
    }

    private static class ValueTypeConverter extends StdConverter<String, ValueType> {
        @Override
        public ValueType convert(String value) {
            return ValueType.valueOf(value.toUpperCase(Locale.ENGLISH));
        }
    }

    private static class DefaultValueConverter extends StdConverter<String, DefaultValue> {
        @Override
        public DefaultValue convert(String value) {
            return DefaultValue.of(value.replaceAll("DefaultValue\\(|\\)", ""));
        }
    }

    private static class PropertyStateConverter extends StdConverter<String, GraphStore.PropertyState> {
        @Override
        public GraphStore.PropertyState convert(String value) {
            return GraphStore.PropertyState.valueOf(value);
        }
    }
}