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
package io.gravitee.policy.jsonvalidation.kafka.factory;

import org.apache.kafka.common.message.FetchResponseData;
import org.apache.kafka.common.message.ProduceResponseData;
import org.apache.kafka.common.protocol.Errors;
import org.apache.kafka.common.record.CompressionType;
import org.apache.kafka.common.record.MemoryRecords;
import org.apache.kafka.common.record.RecordBatch;
import org.apache.kafka.common.record.SimpleRecord;
import org.apache.kafka.common.requests.FetchResponse;
import org.apache.kafka.common.requests.ProduceResponse;

/**
 * @author GraviteeSource Team
 */
public class TestKafkaApiMessageFactory {

    public static final String TEST_TOPIC = "test-topic";

    public static ProduceResponse createFailedProduceResponseWithTwoPartitions(Errors errors) {
        ProduceResponseData data = new ProduceResponseData();

        ProduceResponseData.TopicProduceResponse topicResponse = new ProduceResponseData.TopicProduceResponse().setName(TEST_TOPIC);

        topicResponse.partitionResponses().add(createFailedPartitionResponse(errors, 0));
        topicResponse.partitionResponses().add(createFailedPartitionResponse(errors, 1));

        data.responses().add(topicResponse);
        return new ProduceResponse(data);
    }

    public static FetchResponse createFetchResponseWithTwoPartitions() {
        FetchResponseData data = new FetchResponseData();

        FetchResponseData.FetchableTopicResponse topicResponse = new FetchResponseData.FetchableTopicResponse().setTopic(TEST_TOPIC);

        topicResponse.partitions().add(createPartitionDataWithRecord(0));
        topicResponse.partitions().add(createPartitionDataWithRecord(1));
        data.responses().add(topicResponse);

        return new FetchResponse(data);
    }

    private static ProduceResponseData.PartitionProduceResponse createFailedPartitionResponse(Errors errors, int partition) {
        return new ProduceResponseData.PartitionProduceResponse()
            .setIndex(partition)
            .setErrorCode(errors.code())
            .setBaseOffset(-1L)
            .setLogAppendTimeMs(-1L)
            .setLogStartOffset(-1L);
    }

    private static FetchResponseData.PartitionData createPartitionDataWithRecord(int partitionIndex) {
        MemoryRecords records = MemoryRecords.withRecords(
            RecordBatch.CURRENT_MAGIC_VALUE,
            CompressionType.NONE,
            new SimpleRecord("key".getBytes(), "value".getBytes())
        );

        return new FetchResponseData.PartitionData().setPartitionIndex(partitionIndex).setRecords(records);
    }
}
