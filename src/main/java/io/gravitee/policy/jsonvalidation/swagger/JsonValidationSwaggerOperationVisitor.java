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
package io.gravitee.policy.jsonvalidation.swagger;

import io.gravitee.policy.api.swagger.Policy;
import io.gravitee.policy.api.swagger.v2.SwaggerOperationVisitor;
import io.swagger.models.Operation;
import io.swagger.models.Swagger;
import java.util.Optional;

/**
 * @author Florent CHAMFROY (florent.chamfroy at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JsonValidationSwaggerOperationVisitor implements SwaggerOperationVisitor {

    @Override
    public Optional<Policy> visit(Swagger swagger, Operation o) {
        return Optional.empty();
    }
}
