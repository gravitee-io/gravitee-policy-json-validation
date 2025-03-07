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

import static org.assertj.core.api.Assertions.assertThat;

import com.graviteesource.entrypoint.http.post.HttpPostEntrypointConnectorFactory;
import com.graviteesource.reactor.message.MessageApiReactorFactory;
import io.gravitee.apim.gateway.tests.sdk.AbstractPolicyTest;
import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.apim.gateway.tests.sdk.connector.EndpointBuilder;
import io.gravitee.apim.gateway.tests.sdk.connector.EntrypointBuilder;
import io.gravitee.apim.gateway.tests.sdk.connector.fakes.MessageStorage;
import io.gravitee.apim.gateway.tests.sdk.connector.fakes.PersistentMockEndpointConnectorFactory;
import io.gravitee.apim.gateway.tests.sdk.policy.PolicyBuilder;
import io.gravitee.apim.gateway.tests.sdk.reactor.ReactorBuilder;
import io.gravitee.apim.plugin.reactor.ReactorPlugin;
import io.gravitee.definition.model.ExecutionMode;
import io.gravitee.gateway.reactive.reactor.v4.reactor.ReactorFactory;
import io.gravitee.plugin.endpoint.EndpointConnectorPlugin;
import io.gravitee.plugin.entrypoint.EntrypointConnectorPlugin;
import io.gravitee.plugin.entrypoint.http.proxy.HttpProxyEntrypointConnectorFactory;
import io.gravitee.plugin.policy.PolicyPlugin;
import io.gravitee.policy.jsonvalidation.configuration.JsonValidationPolicyConfiguration;
import io.reactivex.rxjava3.core.Completable;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.http.HttpClient;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

@DeployApi({ "/apis/v4-validation.json" })
@GatewayTest(v2ExecutionMode = ExecutionMode.V4_EMULATION_ENGINE)
public class JsonValidationPolicyPublishIntegrationTest
    extends AbstractPolicyTest<JsonValidationPolicy, JsonValidationPolicyConfiguration> {

    static Stream<String> apis() {
        return Stream.of("/a-v4-validation");
    }

    private MessageStorage messageStorage;

    @BeforeEach
    void setUp() {
        messageStorage = getBean(MessageStorage.class);
    }

    @AfterEach
    void tearDown() {
        messageStorage.reset();
    }

    static Stream<TestCase> cases() {
        return Stream.of(
            new TestCase("good JSON object", JsonObject.of("message", "Turing"), false),
            new TestCase("bad JSON object", JsonObject.of("no-user-field", "Turing"), true)
        );
    }

    @TestFactory
    public Stream<DynamicTest> publishPhase(HttpClient client) {
        return apis()
            .flatMap(requestUri -> cases().map(testCase -> testCase.with(requestUri)))
            .map(tuple ->
                DynamicTest.dynamicTest(
                    tuple.displayName(),
                    () -> publishPhase(tuple.body(), tuple.entrypoint(), client, tuple.expectFail())
                )
            );
    }

    public void publishPhase(JsonObject body, String requestUri, HttpClient client, boolean expectFail) throws Exception {
        postMessage(client, requestUri, body, Map.of("X-Test-Header", "header-value")).test().awaitDone(30, TimeUnit.SECONDS);
        if (!expectFail) {
            messageStorage
                .subject()
                .take(1)
                .test()
                .assertValue(message -> {
                    assertThat(new JsonObject(message.content().toString())).isEqualTo(body);
                    return true;
                });
        }
    }

    private Completable postMessage(HttpClient client, String requestURI, JsonObject requestBody, Map<String, String> headers) {
        return client
            .rxRequest(HttpMethod.POST, requestURI)
            .flatMap(request -> {
                headers.forEach(request::putHeader);
                return request.rxSend(requestBody.toString());
            })
            .flatMapCompletable(response -> {
                assertThat(response.statusCode()).isEqualTo(202);
                return response.rxBody().ignoreElement();
            });
    }

    record TestCase(String name, JsonObject body, boolean expectFail, String entrypoint) {
        TestCase(String name, JsonObject body, boolean expectFail) {
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
        entrypoints.putIfAbsent("http-post", EntrypointBuilder.build("http-post", HttpPostEntrypointConnectorFactory.class));
    }

    @Override
    public void configureEndpoints(Map<String, EndpointConnectorPlugin<?, ?>> endpoints) {
        super.configureEndpoints(endpoints);
        endpoints.putIfAbsent("mock", EndpointBuilder.build("mock", PersistentMockEndpointConnectorFactory.class));
    }

    @Override
    public void configurePolicies(Map<String, PolicyPlugin> policies) {
        policies.put("json-validation", PolicyBuilder.build("json-validation", JsonValidationPolicy.class));
    }
}
