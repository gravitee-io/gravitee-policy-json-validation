/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.validation.kafka.wireformat;

/**
 * How the schema id / Avro payload is framed in a Kafka record.
 *
 * <p>Additional framings (e.g. Apicurio's 8-byte global id or a header-carried id) can be added as new values once a
 * matching schema registry resource exists to resolve their ids.
 */
public enum WireFormat {
    /** Confluent wire format: 1 magic byte {@code 0x00} + 4-byte big-endian schema id, then the Avro body. */
    CONFLUENT_4B,
    /** No envelope: the record body is bare Avro and carries no schema id (payload offset 0). Use with EXPRESSION. */
    NONE,
}
