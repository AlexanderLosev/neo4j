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
package org.neo4j.gds;

import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.Parser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public interface ModelSerializer<DATA, PROTO_DATA extends GeneratedMessageV3> {

    PROTO_DATA toSerializable(DATA modelData) throws IOException;

    @NotNull
    DATA deserializeModelData(PROTO_DATA protoModel) throws IOException;

    Parser<PROTO_DATA> modelParser();
}
