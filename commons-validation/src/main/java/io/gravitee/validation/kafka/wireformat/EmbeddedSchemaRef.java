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
 * The schema id extracted from a record's wire format, together with the offset at which the Avro payload begins
 * (i.e. the number of envelope bytes to skip before decoding). For header-based formats the body is the bare payload,
 * so {@code payloadOffset} is 0.
 */
public record EmbeddedSchemaRef(String schemaId, int payloadOffset) {}
