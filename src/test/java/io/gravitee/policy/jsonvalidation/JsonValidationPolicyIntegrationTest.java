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
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;

import com.graviteesource.reactor.message.MessageApiReactorFactory;
import io.gravitee.apim.gateway.tests.sdk.AbstractPolicyTest;
import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.apim.gateway.tests.sdk.connector.EndpointBuilder;
import io.gravitee.apim.gateway.tests.sdk.connector.EntrypointBuilder;
import io.gravitee.apim.gateway.tests.sdk.reactor.ReactorBuilder;
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
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.http.HttpClient;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

@DeployApi({ "/apis/v4-proxy.json" })
@GatewayTest(v2ExecutionMode = ExecutionMode.V4_EMULATION_ENGINE)
public class JsonValidationPolicyIntegrationTest extends AbstractPolicyTest<JsonValidationPolicy, JsonValidationPolicyConfiguration> {

    static Stream<String> apis() {
        return Stream.of("/a-v4-api");
    }

    static Stream<TestCase> cases() {
        return Stream.of(
            new TestCase("good JSON object", JsonObject.of("user", "Turing").toString(), false),
            new TestCase("bad JSON object", JsonObject.of("no-user-field", "Turing").toString(), true),
            new TestCase("not JSON object", "not JSON", true)
        );
    }

    @TestFactory
    public Stream<DynamicTest> requestPhase(HttpClient client) {
        return apis()
            .flatMap(requestUri -> cases().map(testCase -> testCase.with(requestUri)))
            .map(tuple ->
                DynamicTest.dynamicTest(tuple.displayName(), () ->
                    requestPhase(tuple.body(), tuple.entrypoint(), client, tuple.expectFail())
                )
            );
    }

    @TestFactory
    public Stream<DynamicTest> responsePhase(HttpClient client) {
        return apis()
            .flatMap(requestUri -> cases().map(testCase -> testCase.with(requestUri)))
            .map(tuple ->
                DynamicTest.dynamicTest(tuple.displayName(), () ->
                    responsePhase(tuple.body(), tuple.entrypoint(), client, tuple.expectFail())
                )
            );
    }

    public void requestPhase(String body, String requestUri, HttpClient client, boolean expectFail) throws Exception {
        if (!expectFail) {
            wiremock.stubFor(post("/team").willReturn(ok()));
        }

        client
            .rxRequest(POST, requestUri)
            .map(r -> r.putHeader(HttpHeaderNames.CONTENT_TYPE, MediaType.APPLICATION_JSON))
            .flatMap(request -> request.rxSend(body))
            .flatMapPublisher(response -> {
                assertThat(response.statusCode()).isEqualTo(expectFail ? 400 : 200);
                return response.toFlowable();
            })
            .test()
            .await()
            .assertComplete()
            .assertNoErrors();

        if (!expectFail) {
            wiremock.verify(1, postRequestedFor(urlPathEqualTo("/team")).withRequestBody(equalToJson(body)));
        }
    }

    public void responsePhase(String body, String requestUri, HttpClient client, boolean expectFail) throws Exception {
        wiremock.stubFor(get("/team").willReturn(ok(body)));

        client
            .rxRequest(GET, requestUri)
            .map(r -> r.putHeader(HttpHeaderNames.CONTENT_TYPE, MediaType.APPLICATION_JSON))
            .flatMap(HttpClientRequest::rxSend)
            .flatMapPublisher(response -> {
                assertThat(response.statusCode()).isEqualTo(expectFail ? 500 : 200);
                return response.toFlowable();
            })
            .test()
            .await()
            .assertComplete()
            .assertNoErrors();
    }

    record TestCase(String name, String body, boolean expectFail, String entrypoint) {
        TestCase(String name, String body, boolean expectFail) {
            this(name, body, expectFail, null);
        }

        TestCase with(String entrypoint) {
            return new TestCase(name, body, expectFail, entrypoint);
        }

        String displayName() {
            return "should %s with payload %s".formatted(expectFail ? "fail" : "success", name);
        }
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
