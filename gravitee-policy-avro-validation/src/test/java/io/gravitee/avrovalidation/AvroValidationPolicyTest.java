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

import static org.mockito.Mockito.*;

import io.gravitee.avrovalidation.configuration.AvroValidationPolicyConfiguration;
import io.gravitee.avrovalidation.configuration.schema.SerializationForm;
import io.gravitee.avrovalidation.schema.AvroSchemaResolver;
import io.gravitee.avrovalidation.schema.SchemaResolverFactory;
import io.gravitee.gateway.reactive.api.context.kafka.KafkaMessageExecutionContext;
import io.gravitee.gateway.reactive.api.context.kafka.KafkaMessageRequest;
import io.gravitee.gateway.reactive.api.context.kafka.KafkaMessageResponse;
import io.gravitee.gateway.reactive.api.message.kafka.KafkaMessage;
import io.gravitee.gateway.reactive.api.policy.kafka.KafkaPolicy;
import io.gravitee.helpers.SchemaImpl;
import io.gravitee.validation.configuration.errorhandling.NativeErrorHandling;
import io.gravitee.validation.configuration.errorhandling.PublishErrorHandling;
import io.gravitee.validation.configuration.errorhandling.PublishValidationErrorStrategy;
import io.gravitee.validation.configuration.errorhandling.SubscribeErrorHandling;
import io.gravitee.validation.configuration.errorhandling.SubscribeErrorHandlingStrategy;
import io.gravitee.validation.kafka.handler.KafkaValidationResultHandlerFactory;
import io.gravitee.validation.kafka.handler.support.KafkaMessageStub;
import io.gravitee.validation.kafka.handler.support.TestFailHandler;
import io.gravitee.validation.schema.SchemaIdSource;
import io.gravitee.validation.schema.SchemaSource;
import io.gravitee.validation.schema.SchemaSourceType;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class AvroValidationPolicyTest {

    private static final String TEST_HEADER_NAME = "TEST_HEADER_NAME";
    private static String schema = """
        {
          "type": "record",
          "name": "User",
          "namespace": "io.gravitee.avrovalidation",
          "fields": [
            {
              "name": "name",
              "type": "string"
            }
          ]
        }
        """;

    private static byte[] avroMessage = {
        0x00, //magic byte
        0x00,
        0x00,
        0x00,
        0x1B, //schemaid = 27
        0x0C, //length = 12
        0x4D,
        0x61,
        0x63,
        0x69,
        0x65,
        0x6B, //value = Maciek
    };

    private static byte[] wrongAvroMessage = {
        0x00, //magic byte
        0x00,
        0x00,
        0x00,
        0x1B, //schemaid = 27
        0x0C, //length = 12
        0x4D,
        0x61,
        0x63,
        0x69,
        0x65,
        0x6B, //value = Maciek
        0x02, //length = 02
        0x4D, //valie = M
    };

    private KafkaPolicy policy;

    private MockedStatic<SchemaResolverFactory> schemaResolverFactoryMock;
    private MockedStatic<KafkaValidationResultHandlerFactory> handlerFactoryMock;

    @Mock
    private KafkaMessageExecutionContext ctx;

    @Mock
    private AvroSchemaResolver schemaResolver;

    @Mock
    private KafkaMessageRequest request;

    @Mock
    private KafkaMessageResponse response;

    @Captor
    private ArgumentCaptor<Function<KafkaMessage, Maybe<KafkaMessage>>> messageCaptor;

    @Test
    void validateSchemaRequestSuccessTest() {
        var stubMessage = new KafkaMessageStub(avroMessage);
        policy.onMessageRequest(ctx).test().awaitDone(1, TimeUnit.SECONDS);
        verify(request).onMessage(messageCaptor.capture());
        messageCaptor.getValue().apply(stubMessage).test().assertNoErrors();
    }

    @Test
    void validateSchemaRequestFailTest() {
        var stubMessage = new KafkaMessageStub(wrongAvroMessage);
        policy.onMessageRequest(ctx).test().awaitDone(1, TimeUnit.SECONDS);
        verify(request).onMessage(messageCaptor.capture());
        messageCaptor.getValue().apply(stubMessage).test().assertError(IllegalArgumentException.class);
    }

    @Test
    void validateSchemaResponseSuccessTest() {
        var stubMessage = new KafkaMessageStub(avroMessage);
        policy.onMessageResponse(ctx).test().awaitDone(1, TimeUnit.SECONDS);
        verify(response).onMessage(messageCaptor.capture());
        messageCaptor.getValue().apply(stubMessage).test().assertNoErrors();
    }

    @Test
    void validateSchemaResponseFailTest() {
        var stubMessage = new KafkaMessageStub(wrongAvroMessage);
        policy.onMessageResponse(ctx).test().awaitDone(1, TimeUnit.SECONDS);
        verify(response).onMessage(messageCaptor.capture());
        messageCaptor.getValue().apply(stubMessage).test().assertError(IllegalArgumentException.class);
    }

    @AfterEach
    void tearDown() {
        if (handlerFactoryMock != null) handlerFactoryMock.close();
        if (schemaResolverFactoryMock != null) schemaResolverFactoryMock.close();
    }

    @BeforeEach
    void setup() {
        when(schemaResolver.resolveSchema(any(), any())).thenReturn(Single.just(new SchemaImpl(schema)));

        var schemaSource = new SchemaSource();
        schemaSource.setSourceType(SchemaSourceType.SCHEMA_REGISTRY_RESOURCE);
        schemaSource.setResourceName("schemas");

        var publishErrorHandling = new PublishErrorHandling();
        publishErrorHandling.setStrategy(PublishValidationErrorStrategy.FAIL_WITH_INVALID_RECORD);

        var subscribeErrorHandling = new SubscribeErrorHandling();
        subscribeErrorHandling.setStrategy(SubscribeErrorHandlingStrategy.ADD_RECORD_HEADER);
        subscribeErrorHandling.setHeaderName(TEST_HEADER_NAME);

        var nativeErrorHandling = new NativeErrorHandling();
        nativeErrorHandling.setOnPublish(publishErrorHandling);
        nativeErrorHandling.setOnSubscribe(subscribeErrorHandling);

        var configuration = new AvroValidationPolicyConfiguration();
        configuration.setSerializationForm(SerializationForm.CONFLUENT);
        configuration.setSchemaIdSource(SchemaIdSource.NATIVE);
        configuration.setSchemaSource(schemaSource);
        configuration.setNativeErrorHandling(nativeErrorHandling);

        schemaResolverFactoryMock = mockStatic(SchemaResolverFactory.class);
        schemaResolverFactoryMock.when(() -> SchemaResolverFactory.createSchemaResolver(any())).thenReturn(schemaResolver);

        handlerFactoryMock = mockStatic(KafkaValidationResultHandlerFactory.class);
        handlerFactoryMock
            .when(() -> KafkaValidationResultHandlerFactory.createValidationResultHandler(any(PublishErrorHandling.class)))
            .thenReturn(new TestFailHandler());
        handlerFactoryMock
            .when(() -> KafkaValidationResultHandlerFactory.createValidationResultHandler(any(SubscribeErrorHandling.class)))
            .thenReturn(new TestFailHandler());

        policy = new AvroValidationPolicy(configuration);

        lenient().when(ctx.request()).thenReturn(request);
        lenient().when(ctx.response()).thenReturn(response);
    }
}
