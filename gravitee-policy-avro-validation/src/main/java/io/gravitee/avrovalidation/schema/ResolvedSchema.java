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
package io.gravitee.avrovalidation.schema;

import io.gravitee.resource.schema_registry.api.Schema;

/**
 * The schema a record must validate against, plus the offset at which its Avro payload begins (envelope bytes to skip
 * before decoding). {@code payloadOffset} is 0 when there is no envelope (bare Avro or header-carried id).
 */
public record ResolvedSchema(Schema schema, int payloadOffset) {}
