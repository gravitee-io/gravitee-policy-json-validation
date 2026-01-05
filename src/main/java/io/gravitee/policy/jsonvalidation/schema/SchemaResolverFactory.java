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

import io.gravitee.policy.jsonvalidation.configuration.JsonValidationPolicyConfiguration;
import io.gravitee.policy.jsonvalidation.configuration.schema.SchemaSource;
import io.gravitee.policy.jsonvalidation.configuration.schema.SchemaSourceType;

/**
 * @author GraviteeSource Team
 */
public class SchemaResolverFactory {

    public static SchemaResolver createSchemaResolver(JsonValidationPolicyConfiguration configuration) {
        SchemaSource schemaSource = configuration.getSchemaSource();

        if (schemaSource == null) {
            return legacySchemaResolver(configuration);
        }

        SchemaSourceType schemaSourceType = schemaSource.getSourceType();

        return switch (schemaSourceType) {
            case STATIC_SCHEMA -> new StaticSchemaResolver(schemaSource.getStaticSchema());
            case SCHEMA_REGISTRY_RESOURCE -> new ResourceBasedSchemaResolver(
                schemaSource.getResourceName(),
                schemaSource.getSchemaMapping()
            );
        };
    }

    @SuppressWarnings("deprecation")
    private static StaticSchemaResolver legacySchemaResolver(JsonValidationPolicyConfiguration configuration) {
        return new StaticSchemaResolver(configuration.getSchema());
    }
}
