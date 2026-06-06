## Configuration

| Property                | Required                       | Description                                                                                                     | Type                         | Default         |
|-------------------------|--------------------------------|---------------------------------------------------------------------------------------------------------------|------------------------------|-----------------|
| schemaSource            | X                              | Defines the schema source (schema registry resource) used to resolve the validation schema.                   | Schema Source object         |                 |
| schemaIdSource          | X                              | How the schema to validate against is located: `FROM_RECORD` or `EXPRESSION`.                                  | enum                         | FROM_RECORD     |
| wireFormat              | X                              | How the schema id / Avro payload is framed in the record: `CONFLUENT_4B`, `APICURIO_8B`, `HEADER`, `NONE`.     | enum                         | CONFLUENT_4B    |
| schemaIdHeader          | X (for `HEADER` wire format)   | Record header carrying the schema id.                                                                          | string                       |                 |
| schemaIdEvalString      | X (for `EXPRESSION` source)    | EL that evaluates to the schema subject (e.g. `{#message.topic}-value`).                                       | string                       |                 |
| schemaVersionEvalString | X (for `EXPRESSION` source)    | EL that evaluates to the schema version.                                                                       | string                       |                 |
| validationDepth         |                                | `CONTENT` decodes the payload; `SCHEMA_ONLY` only checks the id resolves to the topic subject (no decode).     | enum                         | CONTENT         |
| nativeErrorHandling     | X                              | Error handling strategy for the consumer/producer when used on a Native API (Kafka Gateway protocol).         | Native Error Handling object |                 |

### Schema source strategy

How the schema to validate against is located:

- **`FROM_RECORD`** – use the schema id carried in the record (in the wire-format envelope or a record header) and enforce that it resolves to the schema registered under the topic's subject (`<topic>-value`) before accepting the record. The subject is the authority — a producer cannot validate against an arbitrary registered schema. The payload is then decoded with the producer's (validated) writer schema.
- **`EXPRESSION`** – resolve the schema by an Expression Language mapping that evaluates to a subject and version (e.g. `{#message.topic}-value`), ignoring the producer's id.

### Wire format

How the schema id / Avro payload is framed in the record. This determines where the Avro body starts (envelope to skip) and — for `FROM_RECORD` — where the id is read:

- **`CONFLUENT_4B`** – Confluent wire format: magic byte `0x00` + 4-byte schema id, then the Avro body.
- **`APICURIO_8B`** – Apicurio legacy wire format: magic byte `0x00` + 8-byte global id, then the Avro body.
- **`HEADER`** – schema id carried in a Kafka record header (`schemaIdHeader`); the body is the bare Avro payload.
- **`NONE`** – no envelope; the body is bare Avro with no id (use with `EXPRESSION`).

### Validation depth

- **`CONTENT`** (default) – resolve the schema and deserialize the payload to confirm it conforms. Strongest guarantee; adds one Avro decode per record.
- **`SCHEMA_ONLY`** – verify only that the record's embedded id resolves to the topic subject, without deserializing the payload. Lower latency; requires `schemaIdSource = FROM_RECORD`.

### Native Error Handling

The `nativeErrorHandling` option is available **only for Native API (Kafka Gateway protocol)** and defines the
validation error handling strategy for the `SUBSCRIBE` and `PUBLISH` phases.

#### onSubscribe (SUBSCRIBE phase)

| Strategy             | Description                                                                                                            |
|----------------------|----------------------------------------------------------------------------------------------------------------------|
| INVALIDATE_PARTITION | The affected partition is returned to the consumer with a `CORRUPT_MESSAGE` error and its records stripped.           |
| ADD_RECORD_HEADER    | The record is forwarded with a header (`headerName`) flagging it as invalid. The header value is a fixed, non-sensitive marker (`headerValue`, default a generic message) — the raw error is not exposed to consumers. |

#### onPublish (PUBLISH phase)

| Strategy                 | Description                                                                                     |
|--------------------------|------------------------------------------------------------------------------------------------|
| FAIL_WITH_INVALID_RECORD | The entire produce request is rejected with `INVALID_RECORD`; no messages are delivered to the broker. |
| LOG                      | Audit mode: the violation is logged/metered and the record is forwarded unchanged.             |
