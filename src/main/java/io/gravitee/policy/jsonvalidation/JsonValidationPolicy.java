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

import static io.gravitee.policy.jsonvalidation.JsonValidationPolicy.Source.MESSAGE_REQUEST;
import static io.gravitee.policy.jsonvalidation.JsonValidationPolicy.Source.MESSAGE_RESPONSE;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import io.gravitee.common.http.MediaType;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.reactive.api.ExecutionFailure;
import io.gravitee.gateway.reactive.api.context.http.HttpBaseExecutionContext;
import io.gravitee.gateway.reactive.api.context.http.HttpMessageExecutionContext;
import io.gravitee.gateway.reactive.api.context.http.HttpPlainExecutionContext;
import io.gravitee.gateway.reactive.api.message.Message;
import io.gravitee.gateway.reactive.api.policy.http.HttpPolicy;
import io.gravitee.policy.jsonvalidation.configuration.JsonValidationPolicyConfiguration;
import io.gravitee.policy.v3.jsonvalidation.JsonValidationPolicyV3;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import java.io.IOException;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonValidationPolicy extends JsonValidationPolicyV3 implements HttpPolicy {

    private static final Logger log = LoggerFactory.getLogger(JsonValidationPolicy.class);
    public static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final JsonNode schema;
    private final boolean straightRespond;

    /**
     * Create a new JsonMetadata Policy instance based on its associated configuration
     *
     * @param configuration the associated configuration to the new JsonMetadata Policy instance
     */
    public JsonValidationPolicy(JsonValidationPolicyConfiguration configuration) throws IOException {
        super(configuration);
        schema = JsonLoader.fromString(configuration.getSchema());
        straightRespond = configuration.isStraightRespondMode();
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
            .flatMapCompletable(buffer -> validate(ctx, buffer, Source.REQUEST, false, JsonValidationPolicy::interrupt))
            .onErrorResumeNext(th -> errorHandling(ctx, th.toString(), Source.REQUEST, JsonValidationPolicy::interrupt));
    }

    @Override
    public Completable onResponse(HttpPlainExecutionContext ctx) {
        return ctx
            .response()
            .body()
            .flatMapCompletable(buffer ->
                validate(ctx, buffer, Source.RESPONSE, configuration.isStraightRespondMode(), JsonValidationPolicy::interrupt)
            )
            .onErrorResumeNext(th -> errorHandling(ctx, th.toString(), Source.RESPONSE, JsonValidationPolicy::interrupt));
    }

    @Override
    public Completable onMessageRequest(HttpMessageExecutionContext ctx) {
        return ctx
            .request()
            .onMessages(e ->
                e
                    .map(Message::content)
                    .flatMapCompletable(buffer -> validate(ctx, buffer, MESSAGE_REQUEST, straightRespond, JsonValidationPolicy::interrupt))
                    .onErrorResumeNext(th -> errorHandling(ctx, th.toString(), MESSAGE_REQUEST, JsonValidationPolicy::interrupt))
                    .toFlowable()
            );
    }

    @Override
    public Completable onMessageResponse(HttpMessageExecutionContext ctx) {
        return ctx
            .response()
            .onMessages(e ->
                e
                    .map(Message::content)
                    .flatMapCompletable(buffer -> validate(ctx, buffer, MESSAGE_RESPONSE, straightRespond, JsonValidationPolicy::interrupt))
                    .onErrorResumeNext(th -> errorHandling(ctx, th.toString(), MESSAGE_RESPONSE, JsonValidationPolicy::interrupt))
                    .toFlowable()
            );
    }

    private <T extends HttpBaseExecutionContext> Completable validate(
        T ctx,
        Buffer buffer,
        Source source,
        boolean straightMode,
        BiFunction<T, ExecutionFailure, Completable> interrupt
    ) throws IOException, ProcessingException {
        JsonNode jsonNode = JSON_MAPPER.readTree(buffer.getBytes());
        var report = configuration.isValidateUnchecked()
            ? validator.validateUnchecked(schema, jsonNode)
            : validator.validate(schema, jsonNode);
        if (!report.isSuccess()) {
            log.debug("Invalid body '{}'", report);
        }
        return report.isSuccess()
            ? Completable.complete()
            : errorHandling(ctx, report.toString(), source.status, source.getPayloadKey(), straightMode, interrupt);
    }

    private <T extends HttpBaseExecutionContext> Completable errorHandling(
        T ctx,
        String th,
        Source source,
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
            : errorMessage(ctx, statusCode)
                .flatMapCompletable(msg ->
                    interrupt.apply(ctx, new ExecutionFailure(statusCode).contentType(MediaType.APPLICATION_JSON).key(key).message(msg))
                );
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

    enum Source {
        REQUEST("JSON_INVALID_PAYLOAD", "JSON_INVALID_FORMAT", 400),
        RESPONSE("JSON_INVALID_RESPONSE_FORMAT", "JSON_INVALID_RESPONSE_PAYLOAD", 500),
        MESSAGE_REQUEST("JSON_INVALID_MESSAGE_REQUEST_PAYLOAD", "JSON_INVALID_MESSAGE_REQUEST_FORMAT", 400),
        MESSAGE_RESPONSE("JSON_INVALID_MESSAGE_REQUEST_PAYLOAD", "JSON_INVALID_MESSAGE_REQUEST_FORMAT", 500);

        private final String invalidPayloadKey;
        private final String invalidFormatKey;
        private final int status;

        Source(String invalidPayloadKey, String invalidFormatKey, int status) {
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
