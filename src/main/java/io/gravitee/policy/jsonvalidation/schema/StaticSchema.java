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
package io.gravitee.policy.jsonvalidation.schema;

import io.gravitee.resource.schema_registry.api.Reference;
import io.gravitee.resource.schema_registry.api.Schema;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * @author GraviteeSource Team
 */
@RequiredArgsConstructor
public class StaticSchema implements Schema {

    private static final String STATIC_SCHEMA_ID = "static";
    private static final String STATIC_SCHEMA_SUBJECT = "static";
    private static final String STATIC_SCHEMA_VERSION = "1";

    private final String content;

    @Override
    public String getContent() {
        return content;
    }

    @Override
    public String getId() {
        return STATIC_SCHEMA_ID;
    }

    @Override
    public String getSubject() {
        return STATIC_SCHEMA_SUBJECT;
    }

    @Override
    public String getVersion() {
        return STATIC_SCHEMA_VERSION;
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
