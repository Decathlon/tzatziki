package com.decathlon.tzatziki.kafka;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.AbstractConsumerSeekAware;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import static org.springframework.kafka.support.KafkaHeaders.OFFSET;
import static org.springframework.kafka.support.KafkaHeaders.RECEIVED_KEY;
import static org.springframework.kafka.support.KafkaHeaders.RECEIVED_PARTITION;
import static org.springframework.kafka.support.KafkaHeaders.RECEIVED_TOPIC;

@Service
@Slf4j
public class KafkaUsersListener extends AbstractConsumerSeekAware implements Seeker {
    @SpyBean
    private CountService countService;

    @KafkaListener(topics = "users", groupId = "users-group-id", containerFactory = "defaultFactory")
    public void receivedUser(GenericRecord message) {
        countService.countMessage("users");
        log.error("received user: " + message.toString());
    }

    @KafkaListener(topics = "json-users-input", groupId = "users-group-id", containerFactory = "jsonBatchFactory")
    public void receivedUserAsJson(List<String> messages) {
        for (int idx = 0; idx < messages.size(); idx++) {
            try {
                countService.countMessage("json-users-input");
            } catch (Exception e) {
                throw new BatchListenerFailedException(e.getMessage(), e, idx);
            }
        }
    }

    @KafkaListener(topics = "json-users-with-key", groupId = "users-group-id", containerFactory = "jsonBatchFactory")
    public void receivedUserWithKeyAsJson(@Payload List<String> messages,
                                          @Header(RECEIVED_KEY) List<String> messageKey) {
        for (int idx = 0; idx < messages.size(); idx++) {
            try {
                countService.countMessage("json-users-with-key");
                log.error("received user with messageKey %s".formatted(messageKey.get(idx)));
            } catch (Exception e) {
                throw new BatchListenerFailedException(e.getMessage(), e, idx);
            }
        }
    }

    @KafkaListener(topics = "users-with-headers", groupId = "users-with-headers-group-id", containerFactory = "batchFactory")
    public void receivedUserWithHeader(
            @Payload List<GenericRecord> messagePayloads,
            @Header(RECEIVED_PARTITION) List<Long> partitions,
            @Header(OFFSET) List<Long> offsets,
            @Header(RECEIVED_TOPIC) List<String> topics) {
        log.error("{} messages received", messagePayloads.size());
        for (int i = 0; i < messagePayloads.size(); i++) {
            countService.countMessage("users-with-headers");
            log.error("received user on %s-%s@%s: %s".formatted(
                    topics.get(i),
                    partitions.get(i),
                    offsets.get(i),
                    messagePayloads.get(i)
            ));
        }
    }

    @KafkaListener(topics = "users-with-key", groupId = "users-with-key-group-id", containerFactory = "batchFactory")
    public void receivedUserWithKey(
            @Payload List<GenericRecord> messagePayloads,
            @Header(RECEIVED_PARTITION) List<Long> partitions,
            @Header(RECEIVED_KEY) List<String> messageKey,
            @Header(OFFSET) List<Long> offsets,
            @Header(RECEIVED_TOPIC) List<String> topics) {
        log.error("{} messages received", messagePayloads.size());
        for (int i = 0; i < messagePayloads.size(); i++) {
            countService.countMessage("users-with-key");
            log.error("received user with messageKey %s on %s-%s@%s: %s".formatted(
                    messageKey.get(i),
                    topics.get(i),
                    partitions.get(i),
                    offsets.get(i),
                    messagePayloads.get(i)
            ));
        }
    }


    @KafkaListener(topics = "users-with-group", groupId = "users-with-group-group-id", containerFactory = "defaultFactory")
    public void receivedUserWithGroup(GenericRecord message) {
        countService.countMessage("users-with-group");
        log.error("received user with group: " + message.toString());
    }

    @KafkaListener(topics = "group-with-users", groupId = "group-with-users-group-id", containerFactory = "defaultFactory")
    public void receivedGroupWithUsers(GenericRecord message) {
        countService.countMessage("group-with-users");
        log.error("received group with users: " + message.toString());
    }

    @Override
    public void seekToBeginning(String topic) {
        this.getSeekCallbacks().forEach((tp, callback) -> {
            if (tp.topic().equals(topic)) {
                log.error("seek to beginning of topic {} partition {}", tp.topic(), tp.partition());
                callback.seekToBeginning(tp.topic(), tp.partition());
            }
        });
    }

    @Override
    public void seek(String topic, int offset) {
        this.getSeekCallbacks().forEach((tp, callback) -> {
            if (tp.topic().equals(topic)) {
                log.error("seek to offset {} of topic {} partition {}", offset, tp.topic(), tp.partition());
                callback.seek(tp.topic(), tp.partition(), offset);
            }
        });
    }
}
