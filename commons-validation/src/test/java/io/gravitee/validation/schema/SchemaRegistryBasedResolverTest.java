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
package io.gravitee.validation.schema;

import static org.assertj.core.api.Assertions.assertThat;

import io.gravitee.resource.api.ResourceConfiguration;
import io.gravitee.resource.schema_registry.api.Reference;
import io.gravitee.resource.schema_registry.api.Schema;
import io.gravitee.resource.schema_registry.api.SchemaRegistryResource;
import io.reactivex.rxjava3.core.Maybe;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemaRegistryBasedResolverTest {

    @Test
    void shouldResolveSchemaById() {
        TestSchema schema = new TestSchema("schema-by-id");
        TestSchemaRegistryResource resource = new TestSchemaRegistryResource().withSchemaById("27", schema);

        var resolver = new SchemaRegistryBasedResolver<String, String>(
            context -> resource,
            (SchemaIdExtractor<String, String>) (context, message) -> Maybe.just("27")
        );

        var resolved = resolver.resolveSchema("ctx", "message").blockingGet();

        assertThat(resolved).isSameAs(schema);
        assertThat(resource.lastSchemaId).isEqualTo("27");
        assertThat(resource.lastSchemaReference).isNull();
    }

    @Test
    void shouldResolveSchemaByReference() {
        TestSchema schema = new TestSchema("schema-by-reference");
        TestSchemaRegistryResource resource = new TestSchemaRegistryResource().withSchema("User", "1", schema);

        var resolver = new SchemaRegistryBasedResolver<String, String>(
            context -> resource,
            (SchemaReferenceExtractor<String, String>) (context, message) -> Maybe.just(new SchemaReference("User", "1"))
        );

        var resolved = resolver.resolveSchema("ctx", "message").blockingGet();

        assertThat(resolved).isSameAs(schema);
        assertThat(resource.lastSchemaId).isNull();
        assertThat(resource.lastSchemaReference).isEqualTo("User:1");
    }

    private static final class TestSchemaRegistryResource extends SchemaRegistryResource<ResourceConfiguration> {

        private final Map<String, Schema> schemasById = new java.util.HashMap<>();
        private final Map<String, Schema> schemasByReference = new java.util.HashMap<>();

        private String lastSchemaId;
        private String lastSchemaReference;

        private TestSchemaRegistryResource withSchemaById(String id, Schema schema) {
            schemasById.put(id, schema);
            return this;
        }

        private TestSchemaRegistryResource withSchema(String id, String version, Schema schema) {
            schemasByReference.put(id + ":" + version, schema);
            return this;
        }

        @Override
        public Maybe<Schema> getSchemaById(String id) {
            lastSchemaId = id;
            return Maybe.just(schemasById.get(id));
        }

        @Override
        public Maybe<Schema> getSchema(String id) {
            return Maybe.empty();
        }

        @Override
        public Maybe<Schema> getSchema(String id, boolean fetchReferences) {
            return Maybe.empty();
        }

        @Override
        public Maybe<Schema> getSchema(String id, String version) {
            lastSchemaReference = id + ":" + version;
            return Maybe.just(schemasByReference.get(lastSchemaReference));
        }

        @Override
        public Maybe<Schema> getSchema(String id, String version, boolean fetchReferences) {
            return getSchema(id, version);
        }
    }

    private record TestSchema(String content) implements Schema {
        @Override
        public String getContent() {
            return content;
        }

        @Override
        public String getId() {
            return "";
        }

        @Override
        public String getSubject() {
            return "";
        }

        @Override
        public String getVersion() {
            return "";
        }

        @Override
        public List<Reference> getReferences() {
            return List.of();
        }

        @Override
        public Map<String, String> getDependencies() {
            return Map.of();
        }
    }
}
