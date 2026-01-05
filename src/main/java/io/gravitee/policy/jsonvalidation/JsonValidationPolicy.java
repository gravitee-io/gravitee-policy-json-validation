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
package io.gravitee.policy.jsonvalidation;

import static io.gravitee.policy.jsonvalidation.JsonValidationPolicy.HttpSource.MESSAGE_REQUEST;
import static io.gravitee.policy.jsonvalidation.JsonValidationPolicy.HttpSource.MESSAGE_RESPONSE;
import static io.gravitee.policy.jsonvalidation.handler.kafka.KafkaValidationResultHandlerFactory.createValidationResultHandler;
import static io.gravitee.policy.jsonvalidation.schema.SchemaResolverFactory.createSchemaResolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import io.gravitee.common.http.MediaType;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.reactive.api.ExecutionFailure;
import io.gravitee.gateway.reactive.api.context.http.HttpBaseExecutionContext;
import io.gravitee.gateway.reactive.api.context.http.HttpMessageExecutionContext;
import io.gravitee.gateway.reactive.api.context.http.HttpPlainExecutionContext;
import io.gravitee.gateway.reactive.api.context.kafka.KafkaMessageExecutionContext;
import io.gravitee.gateway.reactive.api.message.kafka.KafkaMessage;
import io.gravitee.gateway.reactive.api.policy.http.HttpPolicy;
import io.gravitee.gateway.reactive.api.policy.kafka.KafkaPolicy;
import io.gravitee.policy.JsonValidationException;
import io.gravitee.policy.jsonvalidation.configuration.JsonValidationPolicyConfiguration;
import io.gravitee.policy.jsonvalidation.configuration.errorhandling.NativeErrorHandling;
import io.gravitee.policy.jsonvalidation.handler.ValidationResultHandler;
import io.gravitee.policy.jsonvalidation.handler.kafka.KafkaValidationResultHandler;
import io.gravitee.policy.jsonvalidation.schema.SchemaResolver;
import io.gravitee.policy.jsonvalidation.schema.ValidatableSchemaResolver;
import io.gravitee.policy.v3.jsonvalidation.JsonValidationPolicyV3;
import io.gravitee.resource.schema_registry.api.Schema;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import java.io.IOException;
import java.util.Optional;
import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;

/**
 * @author GraviteeSource Team
 */
@Slf4j
public class JsonValidationPolicy extends JsonValidationPolicyV3 implements HttpPolicy, KafkaPolicy {

    public static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final boolean straightRespond;

    private final SchemaResolver schemaResolver;

    /**
     * Create a new JsonMetadata Policy instance based on its associated configuration
     *
     * @param configuration the associated configuration to the new JsonMetadata Policy instance
     */
    public JsonValidationPolicy(JsonValidationPolicyConfiguration configuration) throws IOException {
        super(configuration);
        straightRespond = configuration.isStraightRespondMode();
        schemaResolver = createSchemaResolver(configuration);

        validateSchemaResolver();
    }

    @Override
    public String id() {
        return "json-validation";
    }

    @Override
    public Completable onRequest(HttpPlainExecutionContext ctx) {
        return ctx
            .request()
            .body()
            .flatMapCompletable(buffer ->
                schemaResolver
                    .resolveSchema(ctx)
                    .flatMapCompletable(schema -> validate(ctx, buffer, schema, HttpSource.REQUEST, false, JsonValidationPolicy::interrupt))
            )
            .onErrorResumeNext(th -> errorHandling(ctx, th.toString(), HttpSource.REQUEST, JsonValidationPolicy::interrupt));
    }

    @Override
    public Completable onResponse(HttpPlainExecutionContext ctx) {
        return ctx
            .response()
            .body()
            .flatMapCompletable(buffer ->
                schemaResolver
                    .resolveSchema(ctx)
                    .flatMapCompletable(schema ->
                        validate(
                            ctx,
                            buffer,
                            schema,
                            HttpSource.RESPONSE,
                            configuration.isStraightRespondMode(),
                            JsonValidationPolicy::interrupt
                        )
                    )
            )
            .onErrorResumeNext(th -> errorHandling(ctx, th.toString(), HttpSource.RESPONSE, JsonValidationPolicy::interrupt));
    }

    @Override
    public Completable onMessageRequest(HttpMessageExecutionContext ctx) {
        return Completable.defer(() ->
            ctx
                .request()
                .onMessage(message ->
                    schemaResolver
                        .resolveSchema(ctx, message)
                        .flatMapMaybe(schema -> {
                            try {
                                return validate(
                                    ctx,
                                    message.content(),
                                    schema,
                                    MESSAGE_REQUEST,
                                    straightRespond,
                                    JsonValidationPolicy::interrupt
                                ).andThen(Maybe.just(message));
                            } catch (IOException | ProcessingException e) {
                                throw new JsonValidationException("Error occurred during json validation " + e.getMessage());
                            }
                        })
                        .onErrorResumeNext(th ->
                            errorHandling(ctx, th.toString(), MESSAGE_REQUEST, JsonValidationPolicy::interrupt).andThen(Maybe.just(message))
                        )
                )
                .onErrorResumeNext(th -> errorHandling(ctx, th.toString(), MESSAGE_REQUEST, JsonValidationPolicy::interrupt))
        );
    }

    @Override
    public Completable onMessageResponse(HttpMessageExecutionContext ctx) {
        return Completable.defer(() ->
            ctx
                .response()
                .onMessage(message ->
                    schemaResolver
                        .resolveSchema(ctx, message)
                        .flatMapMaybe(schema -> {
                            try {
                                return validate(
                                    ctx,
                                    message.content(),
                                    schema,
                                    MESSAGE_RESPONSE,
                                    straightRespond,
                                    JsonValidationPolicy::interrupt
                                ).andThen(Maybe.just(message));
                            } catch (IOException | ProcessingException e) {
                                throw new JsonValidationException("Error occurred during json validation " + e.getMessage());
                            }
                        })
                        .onErrorResumeNext(th ->
                            errorHandling(ctx, th.toString(), MESSAGE_RESPONSE, JsonValidationPolicy::interrupt).andThen(
                                Maybe.just(message)
                            )
                        )
                )
                .onErrorResumeNext(th -> errorHandling(ctx, th.toString(), MESSAGE_RESPONSE, JsonValidationPolicy::interrupt))
        );
    }

    @Override
    public Completable onMessageRequest(KafkaMessageExecutionContext ctx) {
        return Completable.defer(() -> {
            if (Optional.ofNullable(configuration.getNativeErrorHandling()).map(NativeErrorHandling::onPublish).isEmpty()) {
                return Completable.error(
                    new IllegalArgumentException("JSON-validation policy for Kafka is not configured for onPublish phase.")
                );
            }

            KafkaValidationResultHandler handler = createValidationResultHandler(configuration.getNativeErrorHandling().onPublish());
            return ctx.request().onMessage(message -> validate(ctx, message, handler).andThen(Maybe.just(message)));
        });
    }

    @Override
    public Completable onMessageResponse(KafkaMessageExecutionContext ctx) {
        return Completable.defer(() -> {
            if (Optional.ofNullable(configuration.getNativeErrorHandling()).map(NativeErrorHandling::onSubscribe).isEmpty()) {
                return Completable.error(
                    new IllegalArgumentException("JSON-validation policy for Kafka is not configured for onSubscribe phase.")
                );
            }

            KafkaValidationResultHandler handler = createValidationResultHandler(configuration.getNativeErrorHandling().onSubscribe());
            return ctx.response().onMessage(message -> validate(ctx, message, handler).andThen(Maybe.just(message)));
        });
    }

    private <T extends HttpBaseExecutionContext> Completable validate(
        T ctx,
        Buffer buffer,
        Schema schema,
        HttpSource source,
        boolean straightMode,
        BiFunction<T, ExecutionFailure, Completable> interrupt
    ) throws IOException, ProcessingException {
        var report = validatePayload(buffer, schema);
        if (!report.isSuccess()) {
            log.debug("Invalid body '{}'", report);
        }
        return report.isSuccess()
            ? Completable.complete()
            : errorHandling(ctx, report.toString(), source.status, source.getPayloadKey(), straightMode, interrupt);
    }

    private <T extends KafkaMessageExecutionContext> Completable validate(
        T ctx,
        KafkaMessage message,
        ValidationResultHandler<T, KafkaMessage> handler
    ) {
        return schemaResolver
            .resolveSchema(ctx, message)
            .flatMapCompletable(schema -> {
                try {
                    var report = validatePayload(message.content(), schema);
                    if (!report.isSuccess()) {
                        log.debug("Invalid message body '{}'", report);
                    }
                    return report.isSuccess() ? handler.onSuccess(ctx, message) : errorHandling(ctx, message, report.toString(), handler);
                } catch (IOException | ProcessingException e) {
                    log.error("Error occurred during message validation: {}", e.getMessage(), e);
                    return errorHandling(ctx, message, e.toString(), handler);
                }
            });
    }

    private ProcessingReport validatePayload(Buffer buffer, Schema schema) throws IOException, ProcessingException {
        JsonNode parsedSchema = JsonLoader.fromString(schema.getContent());

        JsonNode jsonNode = JSON_MAPPER.readTree(buffer.getBytes());
        return configuration.isValidateUnchecked()
            ? validator.validateUnchecked(parsedSchema, jsonNode)
            : validator.validate(parsedSchema, jsonNode);
    }

    private void validateSchemaResolver() throws IOException {
        if (schemaResolver instanceof ValidatableSchemaResolver) {
            ((ValidatableSchemaResolver) schemaResolver).validate();
        }
    }

    private <T extends HttpBaseExecutionContext> Completable errorHandling(
        T ctx,
        String th,
        HttpSource source,
        BiFunction<T, ExecutionFailure, Completable> interrupt
    ) {
        return errorHandling(ctx, th, source.status, source.getFormatKey(), false, interrupt);
    }

    private <T extends HttpBaseExecutionContext> Completable errorHandling(
        T ctx,
        String th,
        int statusCode,
        String key,
        boolean straightMode,
        BiFunction<T, ExecutionFailure, Completable> interrupt
    ) {
        ctx.metrics().setErrorMessage(th);
        return straightMode
            ? Completable.complete()
            : errorMessage(ctx, statusCode).flatMapCompletable(msg ->
                interrupt.apply(ctx, new ExecutionFailure(statusCode).contentType(MediaType.APPLICATION_JSON).key(key).message(msg))
            );
    }

    private <T extends KafkaMessageExecutionContext> Completable errorHandling(
        T ctx,
        KafkaMessage kafkaMessage,
        String throwableMessage,
        ValidationResultHandler<T, KafkaMessage> handler
    ) {
        return handler.onError(ctx, kafkaMessage, throwableMessage);
    }

    private Maybe<String> errorMessage(HttpBaseExecutionContext executionContext, int httpStatusCode) {
        String defaultMessage = httpStatusCode == 400 ? BAD_REQUEST : INTERNAL_ERROR;
        return configuration.getErrorMessage() != null && !configuration.getErrorMessage().isEmpty()
            ? executionContext.getTemplateEngine().eval(configuration.getErrorMessage(), String.class)
            : Maybe.just(defaultMessage);
    }

    private static Completable interrupt(HttpPlainExecutionContext ctx, ExecutionFailure executionFailure) {
        return ctx.interruptWith(executionFailure);
    }

    private static Completable interrupt(HttpMessageExecutionContext ctx, ExecutionFailure executionFailure) {
        return ctx.interruptMessageWith(executionFailure).ignoreElement();
    }

    enum HttpSource {
        REQUEST("JSON_INVALID_PAYLOAD", "JSON_INVALID_FORMAT", 400),
        RESPONSE("JSON_INVALID_RESPONSE_FORMAT", "JSON_INVALID_RESPONSE_PAYLOAD", 500),
        MESSAGE_REQUEST("JSON_INVALID_MESSAGE_REQUEST_PAYLOAD", "JSON_INVALID_MESSAGE_REQUEST_FORMAT", 400),
        MESSAGE_RESPONSE("JSON_INVALID_MESSAGE_RESPONSE_PAYLOAD", "JSON_INVALID_MESSAGE_RESPONSE_FORMAT", 400);

        private final String invalidPayloadKey;
        private final String invalidFormatKey;
        private final int status;

        HttpSource(String invalidPayloadKey, String invalidFormatKey, int status) {
            this.invalidPayloadKey = invalidPayloadKey;
            this.invalidFormatKey = invalidFormatKey;
            this.status = status;
        }

        String getPayloadKey() {
            return invalidPayloadKey;
        }

        String getFormatKey() {
            return invalidFormatKey;
        }
    }
}
