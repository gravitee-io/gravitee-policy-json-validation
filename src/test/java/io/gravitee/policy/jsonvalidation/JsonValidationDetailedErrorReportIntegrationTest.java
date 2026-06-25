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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.vertx.core.http.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graviteesource.reactor.message.MessageApiReactorFactory;
import io.gravitee.apim.gateway.tests.sdk.AbstractPolicyTest;
import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.apim.gateway.tests.sdk.connector.EndpointBuilder;
import io.gravitee.apim.gateway.tests.sdk.connector.EntrypointBuilder;
import io.gravitee.apim.gateway.tests.sdk.reactor.ReactorBuilder;
import io.gravitee.apim.gateway.tests.sdk.reporter.FakeReporter;
import io.gravitee.apim.plugin.reactor.ReactorPlugin;
import io.gravitee.common.http.MediaType;
import io.gravitee.definition.model.ExecutionMode;
import io.gravitee.gateway.api.http.HttpHeaderNames;
import io.gravitee.gateway.reactive.reactor.v4.reactor.ReactorFactory;
import io.gravitee.plugin.endpoint.EndpointConnectorPlugin;
import io.gravitee.plugin.endpoint.http.proxy.HttpProxyEndpointConnectorFactory;
import io.gravitee.plugin.endpoint.mock.MockEndpointConnectorFactory;
import io.gravitee.plugin.entrypoint.EntrypointConnectorPlugin;
import io.gravitee.plugin.entrypoint.http.proxy.HttpProxyEntrypointConnectorFactory;
import io.gravitee.policy.jsonvalidation.configuration.JsonValidationPolicyConfiguration;
import io.gravitee.reporter.api.v4.metric.Metrics;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.core.http.HttpClient;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@DeployApi(
    {
        "/apis/v4-detailed-request.json",
        "/apis/v4-detailed-request-code.json",
        "/apis/v4-detailed-per-instance.json",
        "/apis/v4-detailed-response.json",
        "/apis/v4-straight-respond-flag-on.json",
        "/apis/v4-straight-respond-flag-off.json",
    }
)
@GatewayTest(v2ExecutionMode = ExecutionMode.V4_EMULATION_ENGINE)
public class JsonValidationDetailedErrorReportIntegrationTest
    extends AbstractPolicyTest<JsonValidationPolicy, JsonValidationPolicyConfiguration> {

    private static final String AGE_VIOLATION =
        "/age — instance type (string) does not match any allowed primitive type (allowed: [\"integer\"])";
    private static final String EMAIL_VIOLATION = "/email — string \"x\" is too short (length: 1, required minimum: 5)";
    private static final String CODE_TOO_LONG_VIOLATION = "string \"${T(java.lang.Runtime)}\" is too long (length: 23, maximum allowed: 3)";

    private static final String VALID_BODY = "{\"name\":\"Ada\",\"age\":2,\"email\":\"ada@x.io\"}";
    private static final String INVALID_TYPE_BODY = "{\"name\":\"Ada\",\"age\":\"twenty\",\"email\":\"x\"}";

    private BehaviorSubject<Metrics> metricsSubject;

    @BeforeEach
    void captureMetrics() {
        metricsSubject = BehaviorSubject.create();

        FakeReporter fakeReporter = getBean(FakeReporter.class);
        fakeReporter.setReportableHandler(reportable -> {
            if (reportable instanceof Metrics metrics) {
                metricsSubject.onNext(metrics.toBuilder().build());
            }
        });
    }

    private String postAndReadBody(HttpClient client, String path, String body, int expectedStatus) {
        return client
            .rxRequest(POST, path)
            .map(r ->
                r
                    .putHeader(HttpHeaderNames.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .putHeader(HttpHeaderNames.ACCEPT, MediaType.TEXT_PLAIN)
            )
            .flatMap(request -> request.rxSend(body))
            .flatMap(response -> {
                assertThat(response.statusCode()).isEqualTo(expectedStatus);
                return response.rxBody();
            })
            .map(Buffer::toString)
            .blockingGet();
    }

    @Test
    void requestDetailSurfacedInJsonEnvelopeWhenClientAcceptsJson(HttpClient client) throws Exception {
        String body = client
            .rxRequest(POST, "/detailed-request")
            .map(r ->
                r
                    .putHeader(HttpHeaderNames.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .putHeader(HttpHeaderNames.ACCEPT, MediaType.APPLICATION_JSON)
            )
            .flatMap(request -> request.rxSend(INVALID_TYPE_BODY))
            .flatMap(response -> {
                assertThat(response.statusCode()).isEqualTo(400);
                return response.rxBody();
            })
            .map(Buffer::toString)
            .blockingGet();

        JsonNode envelope = new ObjectMapper().readTree(body);
        assertThat(envelope.get("http_status_code").asInt()).isEqualTo(400);
        assertThat(envelope.get("message").asText()).contains(AGE_VIOLATION);
    }

    @Test
    void requestDetailEchoesElTokenVerbatim(HttpClient client) {
        String message = postAndReadBody(client, "/detailed-request-code", "{\"code\":\"${T(java.lang.Runtime)}\"}", 400);

        assertThat(message).contains(CODE_TOO_LONG_VIOLATION);
    }

    @Test
    void requestDetailIsNotDoubleEncoded(HttpClient client) {
        String message = postAndReadBody(client, "/detailed-request", INVALID_TYPE_BODY, 400);

        assertThat(message).contains(AGE_VIOLATION);
        assertThat(message).doesNotContain("{\"message\"");
        assertThat(message).doesNotContain("http_status_code");
    }

    @Test
    void perInstanceFlagHonouredIndependently(HttpClient client) {
        String requestMessage = postAndReadBody(client, "/detailed-per-instance", INVALID_TYPE_BODY, 400);
        assertThat(requestMessage).contains(AGE_VIOLATION);

        wiremock.stubFor(post("/team").willReturn(ok(INVALID_TYPE_BODY)));
        String responseMessage = client
            .rxRequest(POST, "/detailed-per-instance")
            .map(r ->
                r
                    .putHeader(HttpHeaderNames.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .putHeader(HttpHeaderNames.ACCEPT, MediaType.TEXT_PLAIN)
            )
            .flatMap(request -> request.rxSend(VALID_BODY))
            .flatMap(response -> {
                assertThat(response.statusCode()).isEqualTo(500);
                return response.rxBody();
            })
            .map(Buffer::toString)
            .blockingGet();

        assertThat(responseMessage).isEqualTo("Internal Error");
    }

    @Test
    void responseDetailReturnedOnFailingPayload(HttpClient client) {
        wiremock.stubFor(post("/team").willReturn(ok(INVALID_TYPE_BODY)));

        String message = postAndReadBody(client, "/detailed-response", VALID_BODY, 500);

        assertThat(message).contains(AGE_VIOLATION);
        assertThat(message).contains(EMAIL_VIOLATION);
    }

    @Test
    void concurrentRequestsAreIsolated(HttpClient client) throws InterruptedException {
        String payloadA = "{\"name\":\"Ada\",\"age\":\"twenty\",\"email\":\"ada@x.io\"}";
        String payloadB = "{\"name\":\"Bob\",\"age\":7,\"email\":\"x\"}";

        var testA = client
            .rxRequest(POST, "/detailed-request")
            .map(r ->
                r
                    .putHeader(HttpHeaderNames.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .putHeader(HttpHeaderNames.ACCEPT, MediaType.TEXT_PLAIN)
            )
            .flatMap(request -> request.rxSend(payloadA))
            .flatMap(response -> {
                assertThat(response.statusCode()).isEqualTo(400);
                return response.rxBody();
            })
            .map(Buffer::toString)
            .test();
        var testB = client
            .rxRequest(POST, "/detailed-request")
            .map(r ->
                r
                    .putHeader(HttpHeaderNames.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .putHeader(HttpHeaderNames.ACCEPT, MediaType.TEXT_PLAIN)
            )
            .flatMap(request -> request.rxSend(payloadB))
            .flatMap(response -> {
                assertThat(response.statusCode()).isEqualTo(400);
                return response.rxBody();
            })
            .map(Buffer::toString)
            .test();

        testA.await();
        testB.await();

        String bodyA = testA.values().get(0);
        String bodyB = testB.values().get(0);

        assertThat(bodyA).contains(AGE_VIOLATION);
        assertThat(bodyA).doesNotContain(EMAIL_VIOLATION);
        assertThat(bodyB).contains(EMAIL_VIOLATION);
        assertThat(bodyB).doesNotContain(AGE_VIOLATION);
    }

    @Test
    void straightRespondModePassesThroughOnFlagOn(HttpClient client) {
        wiremock.stubFor(post("/team").willReturn(ok(INVALID_TYPE_BODY)));

        String body = postAndReadBody(client, "/straight-respond-flag-on", VALID_BODY, 200);

        assertThat(body).isEqualTo(INVALID_TYPE_BODY);
    }

    @Test
    void straightRespondModeRecordsMetricsOnFlagOn(HttpClient client) {
        wiremock.stubFor(post("/team").willReturn(ok(INVALID_TYPE_BODY)));

        postAndReadBody(client, "/straight-respond-flag-on", VALID_BODY, 200);

        metricsSubject
            .take(1)
            .test()
            .awaitDone(10, TimeUnit.SECONDS)
            .assertValue(metrics -> {
                assertThat(metrics.getErrorMessage())
                    .isNotBlank()
                    .contains("instance type (string) does not match any allowed primitive type (allowed: [\"integer\"])")
                    .contains("string \"x\" is too short (length: 1, required minimum: 5)");
                return true;
            });
    }

    @Test
    void straightRespondModePassesThroughOnFlagOff(HttpClient client) {
        wiremock.stubFor(post("/team").willReturn(ok(INVALID_TYPE_BODY)));

        String body = postAndReadBody(client, "/straight-respond-flag-off", VALID_BODY, 200);

        assertThat(body).isEqualTo(INVALID_TYPE_BODY);
    }

    @Test
    void straightRespondModeRecordsMetricsOnFlagOff(HttpClient client) {
        wiremock.stubFor(post("/team").willReturn(ok(INVALID_TYPE_BODY)));

        postAndReadBody(client, "/straight-respond-flag-off", VALID_BODY, 200);

        metricsSubject
            .take(1)
            .test()
            .awaitDone(10, TimeUnit.SECONDS)
            .assertValue(metrics -> {
                assertThat(metrics.getErrorMessage())
                    .isNotBlank()
                    .contains("instance type (string) does not match any allowed primitive type (allowed: [\"integer\"])")
                    .contains("string \"x\" is too short (length: 1, required minimum: 5)");
                return true;
            });
    }

    @Override
    public void configureReactors(Set<ReactorPlugin<? extends ReactorFactory<?>>> reactors) {
        reactors.add(ReactorBuilder.build(MessageApiReactorFactory.class));
    }

    @Override
    public void configureEntrypoints(Map<String, EntrypointConnectorPlugin<?, ?>> entrypoints) {
        entrypoints.putIfAbsent("http-proxy", EntrypointBuilder.build("http-proxy", HttpProxyEntrypointConnectorFactory.class));
    }

    @Override
    public void configureEndpoints(Map<String, EndpointConnectorPlugin<?, ?>> endpoints) {
        endpoints.putIfAbsent("http-proxy", EndpointBuilder.build("http-proxy", HttpProxyEndpointConnectorFactory.class));
        endpoints.putIfAbsent("mock", EndpointBuilder.build("mock", MockEndpointConnectorFactory.class));
    }
}
