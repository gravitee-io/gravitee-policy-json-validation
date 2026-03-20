## Configuration

| Property                | Required                 | Description                                                                                                     | Type                         | Default                 |
|-------------------------|--------------------------|-----------------------------------------------------------------------------------------------------------------|------------------------------|-------------------------|
| scope                   | X                        | Policy scope from where the policy is executed                                                                  | Policy scope                 | REQUEST_CONTENT         |
| schemaSource            | X                        | Defines the schema source to resolve the validation schema.                                                     | Schema Source object         |                         |
| schemaIdSource          | X                        | Defines the source of schema id: native for confluent form or SpEL for both confluent and simple.               | Schema Source object         |                         |
| serializationForm       | X                        | Defines the serialization form: confluent or simple.                                                            | Schema Source object         |                         |
| schemaIdEvalString      | X (for Custom id source) | Defines the SpEL for schema ID.                                                                                 | Schema Source object         |                         |
| schemaVersionEvalString | X (for Custom id source) | Defines the SpEL for schema version.                                                                            | Schema Source object         |                         |
| nativeErrorHandling     | X                        | Defines error handling strategy for consumer/producer if policy is used in native API (Kafka Gateway protocol). | Native Error Handling object |                         |

### Schema Source

Specifies the source used to resolve schemas for validation. Only available option is:

- **Resource-based schema registry** – provide the resource name and a mapping expression to resolve the schema subject dynamically (e.g., using `{#message.topic}` to derive the subject from the topic name) or based on native confluent mechanism (schema id encoded on bytes 2 to 5).

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