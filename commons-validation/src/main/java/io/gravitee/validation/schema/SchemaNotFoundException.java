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

import io.gravitee.validation.ValidationException;

public class SchemaNotFoundException extends ValidationException {

    public SchemaNotFoundException() {
        super("Schema not found");
    }

    public SchemaNotFoundException(String id) {
        super(String.format("Schema '%s' not found", id));
    }

    public SchemaNotFoundException(String id, String version) {
        super(String.format("Schema '%s' version '%s' not found", id, version));
    }
}
