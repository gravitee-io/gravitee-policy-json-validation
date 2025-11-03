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
package io.gravitee.policy.jsonvalidation.swagger;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.gravitee.policy.api.swagger.Policy;
import io.gravitee.policy.api.swagger.v3.OAIOperationVisitor;
import io.gravitee.policy.jsonvalidation.configuration.JsonValidationPolicyConfiguration;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Florent CHAMFROY (florent.chamfroy at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JsonValidationOAIOperationVisitor implements OAIOperationVisitor {

    private static final Logger logger = LoggerFactory.getLogger(JsonValidationOAIOperationVisitor.class);

    private final ObjectMapper mapper = JsonMapper
        .builder()
        .configure(JsonWriteFeature.WRITE_NUMBERS_AS_STRINGS, true)
        .serializationInclusion(JsonInclude.Include.NON_NULL)
        .enable(SerializationFeature.INDENT_OUTPUT)
        .build();

    /**
     * openAPI has been parsed with the "resolveFully" option. As a consequence, all $ref have been replaced by proper definition.
     */
    @Override
    public Optional<Policy> visit(io.swagger.v3.oas.models.OpenAPI openAPI, io.swagger.v3.oas.models.Operation operation) {
        String jsonSchema = null;
        final RequestBody requestBody = operation.getRequestBody();
        if (requestBody != null && requestBody.getContent() != null && requestBody.getContent().get("application/json") != null) {
            final var schema = requestBody.getContent().get("application/json").getSchema();
            final var specVersion = schema.getSpecVersion();
            jsonSchema = specVersion == SpecVersion.V31 ? Json31.pretty(schema) : Json.pretty(schema);
        }
        if (!StringUtils.isEmpty(jsonSchema)) {
            var configuration = new JsonValidationPolicyConfiguration();
            try {
                Policy policy = new Policy();
                policy.setName("json-validation");
                configuration.setSchema(jsonSchema);
                policy.setConfiguration(mapper.writeValueAsString(configuration));
                return Optional.of(policy);
            } catch (JsonProcessingException e) {
                logger.error("Failed to serialize json configuration", e);
            }
        }
        return Optional.empty();
    }
}
