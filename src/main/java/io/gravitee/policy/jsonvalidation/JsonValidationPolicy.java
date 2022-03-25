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
package io.gravitee.policy.jsonvalidation;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.github.fge.jsonschema.main.JsonValidator;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.stream.BufferedReadWriteStream;
import io.gravitee.gateway.api.stream.ReadWriteStream;
import io.gravitee.gateway.api.stream.SimpleReadWriteStream;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.PolicyResult;
import io.gravitee.policy.api.annotations.OnRequestContent;
import io.gravitee.policy.api.annotations.OnResponseContent;
import io.gravitee.policy.jsonvalidation.configuration.JsonValidationPolicyConfiguration;
import io.gravitee.policy.jsonvalidation.configuration.PolicyScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
public class JsonValidationPolicy {

    private static final Logger logger = LoggerFactory.getLogger(JsonValidationPolicy.class);

    static final String JSON_INVALID_PAYLOAD_KEY = "JSON_INVALID_PAYLOAD";
    static final String JSON_INVALID_FORMAT_KEY = "JSON_INVALID_FORMAT";
    static final String JSON_INVALID_RESPONSE_PAYLOAD_KEY = "JSON_INVALID_RESPONSE_PAYLOAD";
    static final String JSON_INVALID_RESPONSE_FORMAT_KEY = "JSON_INVALID_RESPONSE_FORMAT";
    private static final String BAD_REQUEST = "Bad Request";
    private static final String INTERNAL_ERROR = "Internal Error";

    /**
     * The associated configuration to this JsonMetadata Policy
     */
    private final JsonValidationPolicyConfiguration configuration;

    private static final JsonValidator validator = JsonSchemaFactory.byDefault().getValidator();

    /**
     * Create a new JsonMetadata Policy instance based on its associated configuration
     *
     * @param configuration the associated configuration to the new JsonMetadata Policy instance
     */
    public JsonValidationPolicy(JsonValidationPolicyConfiguration configuration) {
        this.configuration = configuration;
    }

    @OnRequestContent
    public ReadWriteStream onRequestContent(
        Request request,
        Response response,
        ExecutionContext executionContext,
        PolicyChain policyChain
    ) {
        if (configuration.getScope() == null || configuration.getScope() == PolicyScope.REQUEST_CONTENT) {
            logger.debug("Execute json schema validation policy on request content{}", request.id());

            return new BufferedReadWriteStream() {
                final Buffer buffer = Buffer.buffer();

                @Override
                public SimpleReadWriteStream<Buffer> write(Buffer content) {
                    buffer.appendBuffer(content);
                    return this;
                }

                @Override
                public void end() {
                    try {
                        JsonNode schema = JsonLoader.fromString(configuration.getSchema());
                        JsonNode content = JsonLoader.fromString(buffer.toString());

                        ProcessingReport report = getReport(schema, content);
                        if (!report.isSuccess()) {
                            request.metrics().setMessage(report.toString());
                            sendErrorResponse(JSON_INVALID_PAYLOAD_KEY, executionContext, policyChain, HttpStatusCode.BAD_REQUEST_400);
                        } else {
                            if (buffer.length() > 0) {
                                super.write(buffer);
                            }
                            super.end();
                        }
                    } catch (Exception ex) {
                        request.metrics().setMessage(ex.getMessage());
                        sendErrorResponse(JSON_INVALID_FORMAT_KEY, executionContext, policyChain, HttpStatusCode.BAD_REQUEST_400);
                    }
                }
            };
        }
        return null;
    }

    @OnResponseContent
    public ReadWriteStream onResponseContent(
        Request request,
        Response response,
        ExecutionContext executionContext,
        PolicyChain policyChain
    ) {
        if (configuration.getScope() == PolicyScope.RESPONSE_CONTENT) {
            logger.debug("Execute json schema validation policy on request content{}", request.id());
            return new BufferedReadWriteStream() {
                final Buffer buffer = Buffer.buffer();

                @Override
                public SimpleReadWriteStream<Buffer> write(Buffer content) {
                    buffer.appendBuffer(content);
                    return this;
                }

                @Override
                public void end() {
                    try {
                        JsonNode schema = JsonLoader.fromString(configuration.getSchema());
                        JsonNode content = JsonLoader.fromString(buffer.toString());

                        ProcessingReport report = getReport(schema, content);
                        if (!report.isSuccess()) {
                            request.metrics().setMessage(report.toString());
                            if (!configuration.isStraightRespondMode()) {
                                sendErrorResponse(
                                    JSON_INVALID_RESPONSE_PAYLOAD_KEY,
                                    executionContext,
                                    policyChain,
                                    HttpStatusCode.INTERNAL_SERVER_ERROR_500
                                );
                            } else {
                                // In Straight Respond Mode, send the response to the user without replacement.
                                writeBufferAndEnd();
                            }
                        } else {
                            writeBufferAndEnd();
                        }
                    } catch (Exception ex) {
                        request.metrics().setMessage(ex.toString());
                        if (!configuration.isStraightRespondMode()) {
                            sendErrorResponse(
                                JSON_INVALID_RESPONSE_FORMAT_KEY,
                                executionContext,
                                policyChain,
                                HttpStatusCode.INTERNAL_SERVER_ERROR_500
                            );
                        }
                    }
                }

                private void writeBufferAndEnd() {
                    if (buffer.length() > 0) {
                        super.write(buffer);
                    }
                    super.end();
                }
            };
        }
        return null;
    }

    private ProcessingReport getReport(JsonNode schema, JsonNode content) throws ProcessingException {
        if (configuration.isValidateUnchecked()) {
            return validator.validateUnchecked(schema, content, configuration.isDeepCheck());
        } else {
            return validator.validate(schema, content, configuration.isDeepCheck());
        }
    }

    private void sendErrorResponse(String key, ExecutionContext executionContext, PolicyChain policyChain, int httpStatusCode) {
        if (configuration.getErrorMessage() != null && !configuration.getErrorMessage().isEmpty()) {
            String errorMessage = executionContext.getTemplateEngine().convert(configuration.getErrorMessage());
            policyChain.streamFailWith(PolicyResult.failure(key, httpStatusCode, errorMessage, MediaType.APPLICATION_JSON));
        } else {
            String errorMessage = httpStatusCode == 400 ? BAD_REQUEST : INTERNAL_ERROR;
            policyChain.streamFailWith(PolicyResult.failure(key, httpStatusCode, errorMessage, MediaType.TEXT_PLAIN));
        }
    }
}
