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

import io.gravitee.resource.schema_registry.api.Schema;
import io.gravitee.resource.schema_registry.api.SchemaRegistryResource;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SchemaRegistryBasedResolver<C, M> implements SchemaResolver<C, M, Schema> {

    private final Function<C, SchemaRegistryResource<?>> schemaRegistryResourceProvider;
    private final SchemaIdExtractor<C, M> schemaIdExtractor;
    private final SchemaReferenceExtractor<C, M> schemaReferenceExtractor;

    public SchemaRegistryBasedResolver(
        Function<C, SchemaRegistryResource<?>> schemaRegistryResourceProvider,
        SchemaIdExtractor<C, M> schemaIdExtractor
    ) {
        this.schemaRegistryResourceProvider = schemaRegistryResourceProvider;
        this.schemaIdExtractor = schemaIdExtractor;
        this.schemaReferenceExtractor = null;
    }

    public SchemaRegistryBasedResolver(
        Function<C, SchemaRegistryResource<?>> schemaRegistryResourceProvider,
        SchemaReferenceExtractor<C, M> schemaReferenceExtractor
    ) {
        this.schemaRegistryResourceProvider = schemaRegistryResourceProvider;
        this.schemaIdExtractor = null;
        this.schemaReferenceExtractor = schemaReferenceExtractor;
    }

    @Override
    public Single<Schema> resolveSchema(C context, M message) {
        SchemaRegistryResource<?> schemaRegistryResource = schemaRegistryResourceProvider.apply(context);
        if (schemaRegistryResource == null) {
            log.error("Unable to resolve schema registry resource");
            return Single.error(new IllegalStateException("Unable to resolve schema registry resource"));
        }

        Maybe<Schema> schemaLookup = schemaReferenceExtractor != null
            ? schemaReferenceExtractor
                .resolveSchemaReference(context, message)
                .flatMap(reference -> schemaRegistryResource.getSchema(reference.id(), reference.version()))
            : schemaIdExtractor.resolveSchemaId(context, message).flatMap(schemaRegistryResource::getSchemaById);

        return schemaLookup
            .switchIfEmpty(Maybe.error(new SchemaNotFoundException()))
            .toSingle()
            .doOnError(e -> log.error("Unable to resolve schema", e));
    }
}
