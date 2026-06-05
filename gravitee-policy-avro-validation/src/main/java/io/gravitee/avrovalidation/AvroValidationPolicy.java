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
package io.gravitee.avrovalidation;

import static io.gravitee.avrovalidation.schema.SchemaResolverFactory.createSchemaResolver;
import static io.gravitee.validation.kafka.handler.KafkaValidationResultHandlerFactory.createValidationResultHandler;

import io.gravitee.avrovalidation.configuration.AvroValidationPolicyConfiguration;
import io.gravitee.avrovalidation.configuration.schema.SerializationForm;
import io.gravitee.avrovalidation.schema.AvroSchemaResolver;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.reactive.api.context.kafka.KafkaMessageExecutionContext;
import io.gravitee.gateway.reactive.api.message.kafka.KafkaMessage;
import io.gravitee.gateway.reactive.api.policy.kafka.KafkaPolicy;
import io.gravitee.resource.schema_registry.api.Schema;
import io.gravitee.validation.configuration.errorhandling.NativeErrorHandling;
import io.gravitee.validation.kafka.handler.KafkaValidationResultHandler;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import java.io.IOException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;

/**
 * @author GraviteeSource Team
 */
@Slf4j
public class AvroValidationPolicy implements KafkaPolicy {

    private final AvroSchemaResolver schemaResolver;
    private final AvroValidationPolicyConfiguration configuration;

    public AvroValidationPolicy(AvroValidationPolicyConfiguration configuration) {
        this.configuration = configuration;
        this.schemaResolver = createSchemaResolver(configuration);
    }

    @Override
    public String id() {
        return "avro-validation";
    }

    @Override
    public Completable onMessageRequest(KafkaMessageExecutionContext ctx) {
        return Completable.defer(() -> {
            if (Optional.ofNullable(configuration.getNativeErrorHandling()).map(NativeErrorHandling::getOnPublish).isEmpty()) {
                return Completable.error(
                    new IllegalArgumentException("AVRO-validation policy for Kafka is not configured for onPublish phase.")
                );
            }

            KafkaValidationResultHandler handler = createValidationResultHandler(configuration.getNativeErrorHandling().getOnPublish());
            return ctx.request().onMessage(message -> validate(ctx, message, handler).andThen(Maybe.just(message)));
        });
    }

    @Override
    public Completable onMessageResponse(KafkaMessageExecutionContext ctx) {
        return Completable.defer(() -> {
            if (Optional.ofNullable(configuration.getNativeErrorHandling()).map(NativeErrorHandling::getOnSubscribe).isEmpty()) {
                return Completable.error(
                    new IllegalArgumentException("AVRO-validation policy for Kafka is not configured for onSubscribe phase.")
                );
            }

            KafkaValidationResultHandler handler = createValidationResultHandler(configuration.getNativeErrorHandling().getOnSubscribe());
            return ctx.response().onMessage(message -> validate(ctx, message, handler).andThen(Maybe.just(message)));
        });
    }

    private Completable validate(KafkaMessageExecutionContext ctx, KafkaMessage message, KafkaValidationResultHandler handler) {
        final Buffer content = message.content();

        // Handle tombstones / empty payloads before schema resolution (avoids NPE during schema-id extraction).
        if (content == null) {
            return configuration.isAllowNulls()
                ? handler.onSuccess(ctx, message)
                : handler.onError(ctx, message, "Null record content is not allowed");
        }
        if (content.length() == 0) {
            return configuration.isAllowEmpty()
                ? handler.onSuccess(ctx, message)
                : handler.onError(ctx, message, "Empty record content is not allowed");
        }

        return schemaResolver
            .resolveSchema(ctx, message)
            .map(schema -> isValidAvroBinary(content, schema, configuration.getSerializationForm()))
            // Route schema-resolution failures (no envelope, registry unavailable, schema not found) through the
            // same configured handler as content failures, instead of erroring the message stream. The handler's
            // own outcome (e.g. an interruption on FAIL) is produced downstream and propagates normally.
            .onErrorReturn(ValidationResult::new)
            .flatMapCompletable(result -> {
                if (!result.isSuccess()) {
                    log.debug("Invalid message body: {}", result.getErrorMessage());
                }
                return result.isSuccess() ? handler.onSuccess(ctx, message) : handler.onError(ctx, message, result.getErrorMessage());
            });
    }

    /**
     * Backward-compatible overload assuming the Confluent serialization form.
     */
    public static ValidationResult isValidAvroBinary(Buffer messageContent, Schema writerSchema) {
        return isValidAvroBinary(messageContent, writerSchema, SerializationForm.CONFLUENT);
    }

    public static ValidationResult isValidAvroBinary(Buffer messageContent, Schema writerSchema, SerializationForm serializationForm) {
        try {
            org.apache.avro.Schema parsedSchema = parseSchema(writerSchema);

            byte[] bytes = messageContent.getBytes();

            int payloadOffset;
            if (serializationForm == SerializationForm.SIMPLE) {
                // Bare Avro binary — no Confluent envelope.
                payloadOffset = 0;
            } else {
                // Confluent wire-format: magic(1) + schemaId(4) + payload
                if (bytes.length < 5) {
                    return new ValidationResult(new IOException("Message too short for Confluent Avro"));
                }
                if (bytes[0] != 0x00) {
                    return new ValidationResult(new IOException("Not a Confluent Avro message (magic byte != 0)"));
                }
                payloadOffset = 5;
            }
            int payloadLength = bytes.length - payloadOffset;

            GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(parsedSchema);

            BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(bytes, payloadOffset, payloadLength, null);
            reader.read(null, decoder);

            if (!decoder.isEnd()) {
                return new ValidationResult(new IOException("Trailing bytes after valid Avro payload"));
            }

            return new ValidationResult(true);
        } catch (Exception e) {
            return new ValidationResult(e);
        }
    }

    private static org.apache.avro.Schema parseSchema(Schema writerSchema) {
        if (writerSchema == null || writerSchema.getContent() == null || writerSchema.getContent().isBlank()) {
            throw new IllegalArgumentException("Avro schema is empty");
        }
        return new org.apache.avro.Schema.Parser().parse(writerSchema.getContent());
    }
}
