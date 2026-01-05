You can use the `json-validation` policy to validate JSON payloads. This policy
uses [JSON Schema Validator](https://github.com/java-json-tools/json-schema-validator).

For HTTP protocols: it returns 400 BAD REQUEST when request validation fails and 500 INTERNAL ERROR when response
validation fails, with a custom error message body.

For native protocols (Kafka Gateway): it executes configured strategy (rejects produce request, invalidates partition or
appends record header).

It supports multiple sources of the schema used for validation (static or schema registry resource based with dynamic
schema subject mapping).

It can inject processing report messages into request metrics for analytics.

## Phase

### V3 engine

| onRequestContent | onResponseContent |
|------------------|-------------------|
| X                | X                 |

### V4 engine

| onRequest | onResponse | onMessageRequest | onMessageResponse |
|-----------|------------|------------------|-------------------|
| X         | X          | X                | X                 |

### Native API - Kafka Gateway

| onRequest | onResponse | PUBLISH (onMessageRequest) | SUBSCRIBE (onMessageResponse) |
|-----------|------------|----------------------------|-------------------------------|
|           |            | X                          | X                             |