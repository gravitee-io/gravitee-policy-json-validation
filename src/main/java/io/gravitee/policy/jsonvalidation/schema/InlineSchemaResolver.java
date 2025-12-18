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
package io.gravitee.policy.jsonvalidation.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.reactive.api.context.base.BaseExecutionContext;
import java.io.IOException;

/**
 * @author GraviteeSource Team
 */
public class InlineSchemaResolver implements SchemaResolver {

    private final JsonNode parsedSchema;

    InlineSchemaResolver(String schema) throws IOException {
        this.parsedSchema = JsonLoader.fromString(schema);
    }

    @Override
    public JsonNode resolveSchema(BaseExecutionContext ctx) {
        return parsedSchema;
    }

    @Override
    public JsonNode resolveSchema(ExecutionContext ctx, Request request, Response response) {
        return parsedSchema;
    }
}
