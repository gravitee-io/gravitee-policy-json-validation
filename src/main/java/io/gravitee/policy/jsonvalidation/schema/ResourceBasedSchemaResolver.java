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

import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.reactive.api.context.base.BaseExecutionContext;
import io.gravitee.gateway.reactive.api.context.http.HttpMessageExecutionContext;
import io.gravitee.gateway.reactive.api.context.kafka.KafkaMessageExecutionContext;
import io.gravitee.gateway.reactive.api.message.Message;
import io.gravitee.gateway.reactive.api.message.kafka.KafkaMessage;
import io.gravitee.policy.JsonValidationException;
import io.gravitee.resource.api.ResourceManager;
import io.gravitee.resource.schema_registry.api.Schema;
import io.gravitee.resource.schema_registry.api.SchemaRegistryResource;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

/**
 * @author GraviteeSource Team
 */
@CustomLog
@RequiredArgsConstructor
public class ResourceBasedSchemaResolver implements SchemaResolver {

    private final String resourceName;
    private final String schemaMapping;

    @Override
    public Single<Schema> resolveSchema(BaseExecutionContext ctx) {
        return resolveSchemaByName(ctx, ctx.getTemplateEngine());
    }

    @Override
    public Single<Schema> resolveSchema(HttpMessageExecutionContext ctx, Message message) {
        return resolveSchemaByName(ctx, ctx.getTemplateEngine(message));
    }

    @Override
    public Single<Schema> resolveSchema(KafkaMessageExecutionContext ctx, KafkaMessage message) {
        return resolveSchemaByName(ctx, ctx.getTemplateEngine(message));
    }

    private Single<Schema> resolveSchemaByName(BaseExecutionContext ctx, TemplateEngine templateEngine) {
        SchemaRegistryResource<?> schemaRegistryResource = ctx
            .getComponent(ResourceManager.class)
            .getResource(resourceName, SchemaRegistryResource.class);
        if (schemaRegistryResource == null) {
            ctx.withLogger(log).error("Unable to resolve schema registry resource: " + resourceName);
            return Single.error(new JsonValidationException("Unable to resolve schema registry resource"));
        }

        return resolveSchemaName(ctx, templateEngine)
            .flatMap(schemaRegistryResource::getSchema)
            .switchIfEmpty(Single.error(new JsonValidationException("Unable to resolve schema")))
            .doOnError(e -> ctx.withLogger(log).error("Unable to resolve schema", e));
    }

    private Maybe<String> resolveSchemaName(BaseExecutionContext ctx, TemplateEngine templateEngine) {
        return templateEngine
            .eval(schemaMapping, String.class)
            .doOnError(e -> ctx.withLogger(log).error("Unable to resolve schema name", e));
    }
}
