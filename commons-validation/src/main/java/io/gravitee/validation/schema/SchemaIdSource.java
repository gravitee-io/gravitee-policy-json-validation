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
 * How the schema to validate against is located.
 */
public enum SchemaIdSource {
    /**
     * Use the schema id carried in the record's wire-format envelope, enforced against the
     * schema registered under the topic's subject before accepting it. The subject is the authority — the producer
     * cannot validate against an arbitrary registered schema; the payload is then decoded with the producer's
     * (validated) writer schema.
     */
    FROM_RECORD,
    /**
     * Resolve the schema by an Expression Language mapping that evaluates to a subject and version
     * (e.g. derived from {@code #message.topic}). The producer's embedded id is ignored.
     */
    EXPRESSION,
}
