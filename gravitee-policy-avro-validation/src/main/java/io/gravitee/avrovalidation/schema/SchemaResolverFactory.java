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

import io.gravitee.avrovalidation.configuration.AvroValidationPolicyConfiguration;
import io.gravitee.validation.schema.SchemaIdSource;
import io.gravitee.validation.schema.SchemaSource;
import io.gravitee.validation.schema.SchemaSourceType;

/**
 * @author GraviteeSource Team
 */
public class SchemaResolverFactory {

    private SchemaResolverFactory() {}

    public static AvroSchemaResolver createSchemaResolver(AvroValidationPolicyConfiguration configuration) {
        SchemaSource schemaSource = configuration.getSchemaSource();
        SchemaSourceType schemaSourceType = schemaSource.getSourceType();
        return switch (schemaSourceType) {
            // NATIVE (producer-embedded id) is always gated against the topic subject; EVAL resolves by an EL mapping.
            case SCHEMA_REGISTRY_RESOURCE -> SchemaIdSource.EVAL.equals(configuration.getSchemaIdSource())
                ? new ResourceBasedSchemaResolver(configuration)
                : new SubjectGatedSchemaResolver(configuration);
        };
    }
}
