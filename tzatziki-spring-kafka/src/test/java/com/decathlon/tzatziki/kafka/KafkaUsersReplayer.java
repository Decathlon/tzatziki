package com.decathlon.tzatziki.kafka;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class KafkaUsersReplayer implements Seeker {

    private final CountService countService;
    private final ConsumerFactory<String, GenericRecord> consumerFactory;

    public void seekToBeginning(String topic) {
        consume(topic, Consumer::seekToBeginning);
    }

    @Override
    public void seek(String topic, int offset) {
        consume(topic, (consumer, topicPartitions) ->
                topicPartitions.forEach(topicPartition -> consumer.seek(topicPartition, offset)));
    }

    public void resume(String topic) {
        consume(topic, (consumer, topicPartitions) -> {
        });
    }

    private void consume(String topic, BiConsumer<Consumer<?, GenericRecord>, List<TopicPartition>> seeker) {
        Consumer<String, GenericRecord> consumer = consumerFactory.createConsumer(topic + "-group-id-replay", "");

        Map<String, List<PartitionInfo>> map = consumer.listTopics();

        List<TopicPartition> topicPartitions = map.get(topic).stream()
                .map(partitionInfo -> new TopicPartition(partitionInfo.topic(), partitionInfo.partition()))
                .collect(Collectors.toList());

        consumer.assign(topicPartitions);

        seeker.accept(consumer, topicPartitions);

        while (true) {
            ConsumerRecords<String, GenericRecord> records = consumer.poll(Duration.ofMillis(1000));
            if (records.isEmpty()) {
                break;
            }
            log.info("total records = {}", records.count());
            for (ConsumerRecord<String, GenericRecord> record : records) {
                countService.countMessage(topic);
                log.error("received user on %s-%s@%s: %s".formatted(record.topic(), record.partition(), record.offset(), record.value()));
            }
        }
        consumer.close();
    }
}
