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
package io.gravitee.benchmark;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.gravitee.avrovalidation.AvroValidationPolicy;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.helpers.SchemaImpl;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 8, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Benchmark)
public class PolicyBenchmark {

    private static final String SCHEMA = """
        {
          "type": "record",
          "name": "OrderEvent",
          "namespace": "com.acme.avro.events",
          "doc": "Test schema",

          "fields": [
            {
              "name": "eventId",
              "type": { "type": "string", "logicalType": "uuid" }
            },
            {
              "name": "eventType",
              "type": {
                "type": "enum",
                "name": "OrderEventType",
                "symbols": ["ORDER_CREATED", "PAYMENT_AUTHORIZED", "ORDER_CANCELLED", "ORDER_SHIPPED", "ORDER_RETURNED"]
              }
            },
            {
              "name": "occurredAt",
              "type": { "type": "long", "logicalType": "timestamp-millis" }
            },

            {
              "name": "audit",
              "type": {
                "type": "record",
                "name": "Audit",
                "namespace": "com.acme.avro.common",
                "fields": [
                  { "name": "createdAt", "type": { "type": "long", "logicalType": "timestamp-millis" } },
                  { "name": "createdBy", "type": ["null", "string"], "default": null },
                  { "name": "traceId", "type": ["null", { "type": "string", "logicalType": "uuid" }], "default": null },

                  {
                    "name": "payloadSha256",
                    "type": ["null", {
                      "type": "fixed",
                      "name": "Sha256",
                      "namespace": "com.acme.avro.common",
                      "size": 32
                    }],
                    "default": null
                  },

                  { "name": "tags", "type": { "type": "map", "values": "string" }, "default": {} }
                ]
              }
            },

            { "name": "orderId", "type": "string" },
            { "name": "customerId", "type": ["null", "string"], "default": null },

            {
              "name": "billingAddress",
              "type": ["null", {
                "type": "record",
                "name": "Address",
                "namespace": "com.acme.avro.common",
                "fields": [
                  { "name": "line1", "type": "string" },
                  { "name": "line2", "type": ["null", "string"], "default": null },
                  { "name": "city", "type": "string" },
                  { "name": "postalCode", "type": "string" },
                  { "name": "countryCode", "type": "string", "doc": "np. 'PL'" },

                  {
                    "name": "geo",
                    "type": ["null", {
                      "type": "record",
                      "name": "GeoPoint",
                      "namespace": "com.acme.avro.common",
                      "fields": [
                        { "name": "lat", "type": "double" },
                        { "name": "lon", "type": "double" },
                        { "name": "accuracyMeters", "type": ["null", "float"], "default": null }
                      ]
                    }],
                    "default": null
                  }
                ]
              }],
              "default": null
            },

            {
              "name": "shippingAddress",
              "type": ["null", "com.acme.avro.common.Address"],
              "default": null
            },

            {
              "name": "items",
              "type": {
                "type": "array",
                "items": {
                  "type": "record",
                  "name": "OrderItem",
                  "fields": [
                    { "name": "sku", "type": "string" },
                    { "name": "name", "type": ["null", "string"], "default": null },
                    { "name": "quantity", "type": "int" },

                    {
                      "name": "unitPrice",
                      "type": {
                        "type": "record",
                        "name": "Money",
                        "namespace": "com.acme.avro.common",
                        "fields": [
                          {
                            "name": "amount",
                            "type": {
                              "type": "bytes",
                              "logicalType": "decimal",
                              "precision": 19,
                              "scale": 4
                            }
                          },
                          { "name": "currency", "type": "string", "doc": "ISO-4217, np. 'PLN'" }
                        ]
                      }
                    },

                    {
                      "name": "discount",
                      "type": ["null", "com.acme.avro.common.Money"],
                      "default": null
                    },

                    {
                      "name": "attributes",
                      "type": { "type": "map", "values": ["null", "string"] },
                      "default": {}
                    }
                  ]
                }
              }
            },

            {
              "name": "totals",
              "type": {
                "type": "record",
                "name": "OrderTotals",
                "fields": [
                  { "name": "subtotal", "type": "com.acme.avro.common.Money" },
                  { "name": "shipping", "type": "com.acme.avro.common.Money" },
                  { "name": "tax", "type": "com.acme.avro.common.Money" },
                  { "name": "grandTotal", "type": "com.acme.avro.common.Money" }
                ]
              }
            },

            {
              "name": "payment",
              "type": ["null", {
                "type": "record",
                "name": "PaymentInfo",
                "fields": [
                  {
                    "name": "provider",
                    "type": {
                      "type": "enum",
                      "name": "PaymentProvider",
                      "symbols": ["STRIPE", "ADYEN", "PAYPAL", "MANUAL"]
                    }
                  },
                  { "name": "authorized", "type": "boolean", "default": false },
                  { "name": "authorizationId", "type": ["null", "string"], "default": null },
                  { "name": "amount", "type": "com.acme.avro.common.Money" },
                  {
                    "name": "capturedAt",
                    "type": ["null", { "type": "long", "logicalType": "timestamp-millis" }],
                    "default": null
                  }
                ]
              }],
              "default": null
            },

            {
              "name": "metadata",
              "type": {
                "type": "map",
                "values": ["null", "string", "long", "double", "boolean"]
              },
              "default": {}
            }
          ]
        }
        """;

    private byte[] message;

    private Schema cachedSchema = new Schema.Parser().parse(SCHEMA);

    @Setup(org.openjdk.jmh.annotations.Level.Trial)
    public void setup() {
        message = buildMessage();
    }

    @Benchmark
    public void isValidBenchmark() {
        AvroValidationPolicy.isValidAvroBinary(Buffer.buffer(message), new SchemaImpl(SCHEMA));
    }

    @Benchmark
    public void parseSchemaBenchmark() {
        new org.apache.avro.Schema.Parser().parse(SCHEMA);
    }

    @Benchmark
    public void parseMiniSchemaBenchmark() {
        new org.apache.avro.Schema.Parser().parse(
            """
            {
              "type": "record",
              "name": "OrderEvent",
              "namespace": "com.acme.avro.events",
              "doc": "Test schema",

              "fields": [
                {
                  "name": "eventId",
                  "type": { "type": "string", "logicalType": "uuid" }
                }
                ]
            }
            """
        );
    }

    @Benchmark
    public void isValidBenchmarkCachePrototype() {
        try {
            // 1) Confluent wire-format: magic(1) + schemaId(4) + payload
            if (message.length < 6) {
                return;
            }
            if (message[0] != 0x00) {
                return;
            }

            int payloadOffset = 5;
            int payloadLength = message.length - payloadOffset;

            GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(cachedSchema);

            BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(message, payloadOffset, payloadLength, null);
            reader.read(null, decoder);

            if (!decoder.isEnd()) {
                return;
            }
        } catch (Exception e) {
            return;
        }
    }

    private byte[] buildMessage() {
        Schema parsedSchema = new Schema.Parser().parse(SCHEMA);
        GenericRecord orderEvent = buildOrderEvent(parsedSchema);
        return toBytes("order-events", orderEvent);
    }

    private static GenericRecord buildOrderEvent(Schema orderEventSchema) {
        GenericRecord orderEvent = new GenericData.Record(orderEventSchema);

        orderEvent.put("eventId", "7b2b3f1a-0c8f-4d3f-9cbe-7f1b22a1ad9b");
        orderEvent.put("eventType", new GenericData.EnumSymbol(orderEventSchema.getField("eventType").schema(), "ORDER_CREATED"));
        orderEvent.put("occurredAt", 1736851200123L);

        // ---- audit (com.acme.avro.common.Audit) ----
        Schema auditSchema = orderEventSchema.getField("audit").schema();
        GenericRecord audit = new GenericData.Record(auditSchema);
        audit.put("createdAt", 1736851200123L);

        audit.put("createdBy", "checkout-service");
        audit.put("traceId", "0f3d3c5e-2a8a-4f5f-9c62-8d9a1b2c3d4e");

        // payloadSha256: union ["null", fixed(32)]
        Schema payloadShaSchema = auditSchema.getField("payloadSha256").schema(); // union
        Schema shaFixedSchema = payloadShaSchema
            .getTypes()
            .stream()
            .filter(s -> s.getType() == Schema.Type.FIXED)
            .findFirst()
            .orElseThrow();

        byte[] sha32 = new byte[32];
        GenericData.Fixed shaFixed = new GenericData.Fixed(shaFixedSchema, sha32);
        audit.put("payloadSha256", shaFixed);

        Map<String, String> tags = new HashMap<>();
        tags.put("env", "prod");
        tags.put("region", "eu-central-1");
        audit.put("tags", tags);

        orderEvent.put("audit", audit);

        orderEvent.put("orderId", "ORD-2026-000001");
        orderEvent.put("customerId", "CUST-88421");

        // billingAddress: union ["null", Address]
        Schema billingUnion = orderEventSchema.getField("billingAddress").schema();
        Schema addressSchema = billingUnion
            .getTypes()
            .stream()
            .filter(s -> s.getType() == Schema.Type.RECORD)
            .findFirst()
            .orElseThrow();

        GenericRecord billing = new GenericData.Record(addressSchema);
        billing.put("line1", "ul. Prosta 1");
        billing.put("line2", null);
        billing.put("city", "Warszawa");
        billing.put("postalCode", "00-001");
        billing.put("countryCode", "PL");

        // geo: union ["null", GeoPoint]
        Schema geoUnion = addressSchema.getField("geo").schema();
        Schema geoSchema = geoUnion
            .getTypes()
            .stream()
            .filter(s -> s.getType() == Schema.Type.RECORD)
            .findFirst()
            .orElseThrow();

        GenericRecord geo = new GenericData.Record(geoSchema);
        geo.put("lat", 52.2297);
        geo.put("lon", 21.0122);
        geo.put("accuracyMeters", 15.5f);

        billing.put("geo", geo);
        orderEvent.put("billingAddress", billing);

        GenericRecord shipping = new GenericData.Record(addressSchema);
        shipping.put("line1", "ul. Prosta 1");
        shipping.put("line2", "m. 12");
        shipping.put("city", "Warszawa");
        shipping.put("postalCode", "00-001");
        shipping.put("countryCode", "PL");
        shipping.put("geo", null);
        orderEvent.put("shippingAddress", shipping);

        // ---- items: array of OrderItem ----
        Schema itemsArraySchema = orderEventSchema.getField("items").schema();
        Schema itemSchema = itemsArraySchema.getElementType();

        GenericRecord item1 = new GenericData.Record(itemSchema);
        item1.put("sku", "SKU-123");
        item1.put("name", "Kawa ziarnista 1kg");
        item1.put("quantity", 2);

        // unitPrice: Money (record com.acme.avro.common.Money)
        Schema moneySchema = itemSchema.getField("unitPrice").schema();
        GenericRecord unitPrice1 = new GenericData.Record(moneySchema);

        unitPrice1.put("amount", ByteBuffer.wrap(new byte[] { 0x00, 0x01, 0x02 }));
        unitPrice1.put("currency", "PLN");
        item1.put("unitPrice", unitPrice1);

        item1.put("discount", null);
        Map<String, String> attrs1 = new HashMap<>();
        attrs1.put("roast", "medium");
        attrs1.put("origin", "Brazil");
        item1.put("attributes", attrs1);

        GenericRecord item2 = new GenericData.Record(itemSchema);
        item2.put("sku", "SKU-456");
        item2.put("name", null);
        item2.put("quantity", 1);

        GenericRecord unitPrice2 = new GenericData.Record(moneySchema);
        unitPrice2.put("amount", ByteBuffer.wrap(new byte[] { 0x03, 0x04 }));
        unitPrice2.put("currency", "PLN");
        item2.put("unitPrice", unitPrice2);

        // discount: union ["null", Money]
        GenericRecord discount = new GenericData.Record(moneySchema);
        discount.put("amount", ByteBuffer.wrap(new byte[] { 0x00 }));
        discount.put("currency", "PLN");
        item2.put("discount", discount);

        item2.put("attributes", Map.of("note", "deliver after 17:00"));

        orderEvent.put("items", java.util.List.of(item1, item2));

        // ---- totals: OrderTotals ----
        Schema totalsSchema = orderEventSchema.getField("totals").schema();
        GenericRecord totals = new GenericData.Record(totalsSchema);

        totals.put("subtotal", moneyOf(moneySchema, new byte[] { 0x01 }, "PLN"));
        totals.put("shipping", moneyOf(moneySchema, new byte[] { 0x00 }, "PLN"));
        totals.put("tax", moneyOf(moneySchema, new byte[] { 0x02 }, "PLN"));
        totals.put("grandTotal", moneyOf(moneySchema, new byte[] { 0x03 }, "PLN"));

        orderEvent.put("totals", totals);

        // ---- payment: union ["null", PaymentInfo] ----
        Schema paymentUnion = orderEventSchema.getField("payment").schema();
        Schema paymentSchema = paymentUnion
            .getTypes()
            .stream()
            .filter(s -> s.getType() == Schema.Type.RECORD)
            .findFirst()
            .orElseThrow();

        GenericRecord payment = new GenericData.Record(paymentSchema);
        payment.put("provider", new GenericData.EnumSymbol(paymentSchema.getField("provider").schema(), "STRIPE"));
        payment.put("authorized", true);
        payment.put("authorizationId", "auth_3QxYzAbCdEf");
        payment.put("amount", moneyOf(moneySchema, new byte[] { 0x03 }, "PLN"));
        payment.put("capturedAt", null);

        orderEvent.put("payment", payment);

        // metadata: map of union ["null","string","long","double","boolean"]
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "checkout");
        metadata.put("retryCount", 0L);
        metadata.put("riskScore", 0.12d);
        metadata.put("isTest", false);
        metadata.put("comment", null);

        orderEvent.put("metadata", metadata);

        return orderEvent;
    }

    private static GenericRecord moneyOf(Schema moneySchema, byte[] amountBytes, String currency) {
        GenericRecord money = new GenericData.Record(moneySchema);
        money.put("amount", ByteBuffer.wrap(amountBytes));
        money.put("currency", currency);
        return money;
    }

    public static byte[] toBytes(String topic, GenericRecord genericRecord) {
        KafkaAvroSerializer serializer = new KafkaAvroSerializer();

        Map<String, Object> cfg = new HashMap<>();
        cfg.put("schema.registry.url", "mock://local");

        serializer.configure(cfg, false);

        try {
            byte[] serialize = serializer.serialize(topic, genericRecord);
            System.out.println(toJavaByteArray(serialize));
            return serialize;
        } finally {
            serializer.close();
        }
    }

    public static String toJavaByteArray(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 6);
        sb.append("{ ");

        for (int i = 0; i < bytes.length; i++) {
            sb.append(String.format("0x%02X", bytes[i]));
            if (i < bytes.length - 1) {
                sb.append(", ");
            }
        }

        sb.append(" }");
        return sb.toString();
    }
}
