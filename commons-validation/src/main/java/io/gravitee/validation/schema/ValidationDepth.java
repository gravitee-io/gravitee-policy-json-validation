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
package io.gravitee.validation.schema;

/**
 * How deeply a record is validated.
 */
public enum ValidationDepth {
    /**
     * Trust the schema reference only: verify the record's embedded schema id resolves to the topic's subject,
     * without deserializing the payload. Cheap (a cached registry lookup, no Avro decode). Only meaningful for the
     * {@code FROM_RECORD} schema source.
     */
    SCHEMA_ONLY,
    /**
     * Resolve the schema and additionally deserialize the payload to confirm it conforms. Strongest guarantee; adds
     * one Avro decode per record.
     */
    CONTENT,
}
