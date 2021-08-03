/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.policy.jsonvalidation.swagger;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.gravitee.policy.api.swagger.Policy;
import io.gravitee.policy.api.swagger.v3.OAIOperationVisitor;
import io.gravitee.policy.jsonvalidation.configuration.JsonValidationPolicyConfiguration;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/**
 * @author Florent CHAMFROY (florent.chamfroy at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JsonValidationOAIOperationVisitor implements OAIOperationVisitor {

  private final ObjectMapper mapper = new ObjectMapper();

  {
    mapper.configure(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS, true);
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    mapper.enable(SerializationFeature.INDENT_OUTPUT);
  }

  @Override
  /**
   * openAPI has been parsed with the "resolveFully" option. As a consequence, all $ref have been replaced by proper definition.
   */
  public Optional<Policy> visit(
    io.swagger.v3.oas.models.OpenAPI openAPI,
    io.swagger.v3.oas.models.Operation operation
  ) {
    String jsonSchema = null;
    final RequestBody requestBody = operation.getRequestBody();
    if (
      requestBody != null &&
      requestBody.getContent() != null &&
      requestBody.getContent().get("application/json") != null
    ) {
      final Schema schema = requestBody
        .getContent()
        .get("application/json")
        .getSchema();
      jsonSchema = Json.pretty(schema);
    }
    if (!StringUtils.isEmpty(jsonSchema)) {
      JsonValidationPolicyConfiguration configuration = new JsonValidationPolicyConfiguration();
      try {
        Policy policy = new Policy();
        policy.setName("json-validation");
        configuration.setSchema(jsonSchema);
        policy.setConfiguration(mapper.writeValueAsString(configuration));
        return Optional.of(policy);
      } catch (JsonProcessingException e) {
        e.printStackTrace();
      }
    }
    return Optional.empty();
  }
}
