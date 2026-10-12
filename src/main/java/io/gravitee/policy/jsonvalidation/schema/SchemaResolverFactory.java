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

        if (carriesNoSchema(schemaSource)) {
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

    /**
     * A schema source can carry no schema at all, for instance when a configuration is created through the
     * management API, GitOps or an import with an empty static schema. It must not shadow a populated deprecated
     * {@code schema} field: the empty string cannot be parsed, the policy cannot be instantiated, and the
     * gateway then runs the API without it.
     */
    private static boolean carriesNoSchema(SchemaSource schemaSource) {
        if (schemaSource == null || schemaSource.getSourceType() == null) {
            return true;
        }
        return (
            schemaSource.getSourceType() == SchemaSourceType.STATIC_SCHEMA &&
            (schemaSource.getStaticSchema() == null || schemaSource.getStaticSchema().isBlank())
        );
    }

    @SuppressWarnings("deprecation")
    private static StaticSchemaResolver legacySchemaResolver(JsonValidationPolicyConfiguration configuration) {
        return new StaticSchemaResolver(configuration.getSchema());
    }
}
