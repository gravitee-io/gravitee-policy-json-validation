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
package io.gravitee.policy.v3.jsonvalidation;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.github.fge.jsonschema.main.JsonValidator;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.api.stream.ReadWriteStream;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.PolicyResult;
import io.gravitee.policy.api.annotations.OnRequestContent;
import io.gravitee.policy.api.annotations.OnResponseContent;
import io.gravitee.policy.jsonvalidation.configuration.JsonValidationPolicyConfiguration;
import io.gravitee.policy.jsonvalidation.configuration.PolicyScope;
import io.gravitee.policy.jsonvalidation.configuration.schema.SchemaSource;
import io.gravitee.policy.jsonvalidation.configuration.schema.SchemaSourceType;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
public class JsonValidationPolicyV3 {

    private static final Logger logger = LoggerFactory.getLogger(JsonValidationPolicyV3.class);

    static final String JSON_INVALID_PAYLOAD_KEY = "JSON_INVALID_PAYLOAD";
    static final String JSON_INVALID_FORMAT_KEY = "JSON_INVALID_FORMAT";
    static final String JSON_INVALID_RESPONSE_PAYLOAD_KEY = "JSON_INVALID_RESPONSE_PAYLOAD";
    static final String JSON_INVALID_RESPONSE_FORMAT_KEY = "JSON_INVALID_RESPONSE_FORMAT";
    protected static final String BAD_REQUEST = "Bad Request";
    protected static final String INTERNAL_ERROR = "Internal Error";
    private static final ErrorParams ERROR_PARAMS_ON_RESPONSE_CONTENT = new ErrorParams(
        JSON_INVALID_RESPONSE_PAYLOAD_KEY,
        JSON_INVALID_RESPONSE_FORMAT_KEY,
        HttpStatusCode.INTERNAL_SERVER_ERROR_500
    );
    private static final ErrorParams ERROR_PARAMS_ON_REQUEST_CONTENT = new ErrorParams(
        JSON_INVALID_PAYLOAD_KEY,
        JSON_INVALID_FORMAT_KEY,
        HttpStatusCode.BAD_REQUEST_400
    );

    /**
     * The associated configuration to this JsonMetadata Policy
     */
    protected final JsonValidationPolicyConfiguration configuration;

    protected static final JsonValidator validator = JsonSchemaFactory.byDefault().getValidator();

    /**
     * Create a new JsonMetadata Policy instance based on its associated configuration
     *
     * @param configuration the associated configuration to the new JsonMetadata Policy instance
     */
    public JsonValidationPolicyV3(JsonValidationPolicyConfiguration configuration) {
        this.configuration = configuration;
    }

    @OnRequestContent
    public ReadWriteStream onRequestContent(
        Request request,
        Response response,
        ExecutionContext executionContext,
        PolicyChain policyChain
    ) {
        if (configuration.getScope() != null && configuration.getScope() != PolicyScope.REQUEST_CONTENT) {
            return null;
        }
        return exec(ERROR_PARAMS_ON_REQUEST_CONTENT, false, request, response, executionContext, policyChain);
    }

    @OnResponseContent
    public ReadWriteStream onResponseContent(
        Request request,
        Response response,
        ExecutionContext executionContext,
        PolicyChain policyChain
    ) {
        if (configuration.getScope() != PolicyScope.RESPONSE_CONTENT) {
            return null;
        }
        return exec(
            ERROR_PARAMS_ON_RESPONSE_CONTENT,
            configuration.isStraightRespondMode(),
            request,
            response,
            executionContext,
            policyChain
        );
    }

    private ReadWriteStream exec(
        ErrorParams errorParams,
        boolean straightMode,
        Request request,
        Response response,
        ExecutionContext executionContext,
        PolicyChain policyChain
    ) {
        return new PassThroughBuffer((buffer, writeBufferAndEnd) -> {
            try {
                JsonNode schema = resolveLegacySchema(request, response, executionContext);
                JsonNode content = JsonLoader.fromString(buffer.toString());
                ProcessingReport report = getReport(schema, content);

                if (!report.isSuccess()) {
                    request.metrics().setMessage(report.toString());
                    if (!straightMode) {
                        String detail = configuration.isReturnDetailedErrorReport()
                            ? buildDetailedMessage(schema, content).orElse(null)
                            : null;
                        sendErrorResponse(errorParams.payloadKeyError(), executionContext, policyChain, errorParams.errorStatus(), detail);
                    } else {
                        writeBufferAndEnd.run();
                    }
                } else {
                    writeBufferAndEnd.run();
                }
            } catch (Exception ex) {
                if (ex instanceof RuntimeException) {
                    logger.error("Unexpected error during JSON validation", ex);
                }
                request.metrics().setMessage(ex.toString());
                if (!straightMode) {
                    String key = ex instanceof ProcessingException ? errorParams.payloadKeyError() : errorParams.formatKeyError();
                    sendErrorResponse(key, executionContext, policyChain, errorParams.errorStatus(), null);
                } else {
                    writeBufferAndEnd.run();
                }
            }
        });
    }

    private ProcessingReport getReport(JsonNode schema, JsonNode content) throws ProcessingException {
        if (configuration.isValidateUnchecked()) {
            return validator.validateUnchecked(schema, content, configuration.isDeepCheck());
        } else {
            return validator.validate(schema, content, configuration.isDeepCheck());
        }
    }

    private void sendErrorResponse(
        String key,
        ExecutionContext executionContext,
        PolicyChain policyChain,
        int httpStatusCode,
        String detail
    ) {
        String configuredMessage = configuration.getErrorMessage();
        boolean hasConfiguredMessage = configuredMessage != null && !configuredMessage.isEmpty();
        if (detail != null) {
            policyChain.streamFailWith(PolicyResult.failure(key, httpStatusCode, detail, MediaType.TEXT_PLAIN));
        } else if (hasConfiguredMessage) {
            executionContext
                .getTemplateEngine()
                .eval(configuredMessage, String.class)
                .subscribe(
                    errorMessage ->
                        policyChain.streamFailWith(PolicyResult.failure(key, httpStatusCode, errorMessage, MediaType.TEXT_PLAIN)),
                    throwable -> {
                        logger.warn("Error message evaluation failed: {}", throwable.getMessage(), throwable);
                        String fallbackMessage = httpStatusCode == 400 ? BAD_REQUEST : INTERNAL_ERROR;
                        policyChain.streamFailWith(PolicyResult.failure(key, httpStatusCode, fallbackMessage, MediaType.TEXT_PLAIN));
                    }
                );
        } else {
            String errorMessage = httpStatusCode == 400 ? BAD_REQUEST : INTERNAL_ERROR;
            policyChain.streamFailWith(PolicyResult.failure(key, httpStatusCode, errorMessage, MediaType.TEXT_PLAIN));
        }
    }

    protected Optional<String> buildDetailedMessage(JsonNode schema, JsonNode content) {
        try {
            ProcessingReport deepReport = configuration.isValidateUnchecked()
                ? validator.validateUnchecked(schema, content, true)
                : validator.validate(schema, content, true);

            var details = new LinkedHashSet<String>();
            int count = 0;
            for (ProcessingMessage processingMessage : deepReport) {
                if (count >= 50) {
                    break;
                }
                var json = processingMessage.asJson();
                var pointer = json.path("instance").path("pointer").asText();
                var label = pointer.isBlank() ? "(root)" : pointer;
                var message = json.path("message").asText();
                details.add(label + " — " + message);
                count++;
            }
            return Optional.of(String.join("; ", details)).filter(s -> !s.isBlank());
        } catch (ProcessingException ex) {
            logger.warn("Detailed validation report generation failed: {}", ex.getMessage(), ex);
            return Optional.empty();
        }
    }

    @SuppressWarnings("deprecation")
    // The legacy schema is fixed per policy instance; parse it once and reuse it across requests.
    private volatile JsonNode legacyParsedSchema;

    private JsonNode resolveLegacySchema(Request request, Response response, ExecutionContext executionContext) throws IOException {
        JsonNode cached = legacyParsedSchema;
        if (cached != null) {
            return cached;
        }
        String schemaContent = configuration.getSchema();
        // The deprecated `schema` field may be null when the policy is configured with the newer
        // `schemaSource` form. The V3 engine only supports a static schema; registry sources are V4-only.
        if (schemaContent == null && configuration.getSchemaSource() != null) {
            SchemaSource schemaSource = configuration.getSchemaSource();
            if (schemaSource.getSourceType() == SchemaSourceType.STATIC_SCHEMA) {
                schemaContent = schemaSource.getStaticSchema();
            } else {
                throw new IllegalStateException(
                    "Schema registry sources are not supported by the V3 execution engine; use a static schema or a V4 API"
                );
            }
        }
        if (schemaContent == null) {
            throw new IllegalStateException("No JSON schema configured in the policy");
        }
        synchronized (this) {
            if (legacyParsedSchema == null) {
                legacyParsedSchema = JsonLoader.fromString(schemaContent);
            }
            return legacyParsedSchema;
        }
    }

    private record ErrorParams(String payloadKeyError, String formatKeyError, int errorStatus) {}
}
