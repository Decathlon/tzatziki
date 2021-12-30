package com.decathlon.tzatziki.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.RecoveringBatchErrorHandler;
import org.springframework.kafka.listener.SeekToCurrentErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@Slf4j
public class KafkaConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> batchFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            @Qualifier("avroConsumerFactory") ConsumerFactory consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.setBatchErrorHandler(new RecoveringBatchErrorHandler());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        factory.setBatchListener(true);
        factory.setStatefulRetry(false);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> jsonBatchFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            @Qualifier("jsonConsumerFactory") ConsumerFactory consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.setBatchErrorHandler(new RecoveringBatchErrorHandler());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        factory.setBatchListener(true);
        factory.setStatefulRetry(false);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> defaultFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            @Qualifier("avroConsumerFactory") ConsumerFactory kafkaConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, kafkaConsumerFactory);

        SeekToCurrentErrorHandler errorHandler = new SeekToCurrentErrorHandler((consumerRecord, e) -> {
            log.error("Error during processing of message topic={} partition={} offset={}", consumerRecord.topic(),
                    consumerRecord.partition(), consumerRecord.offset(), e);
        }, new FixedBackOff(200, 2));

        errorHandler.setAckAfterHandle(false);
        factory.setErrorHandler(errorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        factory.setStatefulRetry(false);

        return factory;
    }
}
