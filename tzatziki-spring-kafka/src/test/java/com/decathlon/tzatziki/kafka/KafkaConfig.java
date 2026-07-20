package com.decathlon.tzatziki.kafka;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@SuppressWarnings({"rawtypes","unchecked"})
@Configuration
@Slf4j
public class KafkaConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> batchFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            @Qualifier("avroConsumerFactory") ConsumerFactory consumerFactory) {
        return factory(configurer, consumerFactory);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> avroFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            @Qualifier("avroKeyMessageConsumerFactory") ConsumerFactory avroKeyMessageConsumerFactory) {
        return factory(configurer, avroKeyMessageConsumerFactory);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> jsonBatchFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            @Qualifier("jsonConsumerFactory") ConsumerFactory consumerFactory) {
        return factory(configurer, consumerFactory);
    }

    private static @NonNull ConcurrentKafkaListenerContainerFactory<Object, Object> factory(ConcurrentKafkaListenerContainerFactoryConfigurer configurer, ConsumerFactory consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.setCommonErrorHandler(new DefaultErrorHandler());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        factory.setBatchListener(true);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> defaultFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            @Qualifier("avroConsumerFactory") ConsumerFactory kafkaConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, kafkaConsumerFactory);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler((consumerRecord, e) -> {
            log.error("Error during processing of message topic={} partition={} offset={}", consumerRecord.topic(),
                    consumerRecord.partition(), consumerRecord.offset(), e);
        }, new FixedBackOff(200, 2));

        errorHandler.setAckAfterHandle(false);
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);

        return factory;
    }
}
