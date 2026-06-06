## Overview
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


## Usage
## Configuration

| Property            | Required           | Description                                                                                                                                                                                                                                             | Type                         | Default                 |
|---------------------|--------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------|-------------------------|
| scope               | X                  | Policy scope from where the policy is executed                                                                                                                                                                                                          | Policy scope                 | REQUEST_CONTENT         |
| errorMessage        | X                  | Custom error message in JSON format. SpEL is allowed.                                                                                                                                                                                                   | string                       | {"error":"Bad request"} |
| schema (deprecated) |                    | Deprecated configuration for JSON schema, use schema source instead.                                                                                                                                                                                    | string                       |                         |
| schemaSource        | X                  | Defines the schema source to resolve the validation schema.                                                                                                                                                                                             | Schema Source object         |                         |
| deepCheck           |                    | Validate descendant even if JSON parent container is invalid                                                                                                                                                                                            | boolean                      | false                   |
| validateUnchecked   |                    | Unchecked validation means that conditions which would normally cause the processing to stop with an exception are instead inserted into the resulting report. Warning: anomalous events (e.g. invalid schema or unresolved JSON Reference) are masked. | boolean                      | false                   |
| straightRespondMode |                    | Only for RESPONSE scope. Straight respond mode means that responses failed to validate are still sent to the user without replacement. Validation failure messages are written to metrics for inspection.                                               | boolean                      | false                   |
| nativeErrorHandling | X (for Native API) | Defines error handling strategy for consumer/producer if policy is used in native API (Kafka Gateway protocol).                                                                                                                                         | Native Error Handling object |                         |

### Schema Source

Specifies the source used to resolve schemas for validation. You can choose between:

- **Static schema** – provide the schema definition directly.
- **Resource-based schema registry** – provide the resource name and a mapping expression to resolve the schema subject dynamically (e.g., using `{#message.topic}` to derive the subject from the topic name).

### Native Error Handling

The `nativeErrorHandling` option is available **only for Native API (Kafka Gateway protocol)** and defines the
validation error handling strategy for `SUBSCRIBE` and `PUBLISH` phases.

#### onSubscribe (SUBSCRIBE phase)

| Strategy             | Description                                                                      |
|----------------------|----------------------------------------------------------------------------------|
| INVALIDATE_PARTITION | The entire partition is marked as invalid when a record validation error occurs. |
| ADD_RECORD_HEADER    | A record header containing validation error information is added to the record.  |

#### onPublish (PUBLISH phase)

| Strategy                 | Description                                                                                    |
|--------------------------|------------------------------------------------------------------------------------------------|
| FAIL_WITH_INVALID_RECORD | Entire produce request is failed (all partitions) and no messages are delivered to the broker. |



## Errors
These templates are defined at the API level, in the "Entrypoint" section for v4 APIs, or in "Response Templates" for v2 APIs.
The error keys sent by this policy are as follows:

| Key |
| ---  |
| JSON_INVALID_PAYLOAD |
| JSON_INVALID_FORMAT |
| JSON_INVALID_RESPONSE_PAYLOAD |
| JSON_INVALID_RESPONSE_FORMAT |


