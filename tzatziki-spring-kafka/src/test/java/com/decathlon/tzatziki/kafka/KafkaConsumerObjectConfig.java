package com.decathlon.tzatziki.kafka;

import com.decathlon.tzatziki.steps.KafkaSteps;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.security.auth.SecurityProtocol;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.Map;

@Configuration
public class KafkaConsumerObjectConfig {

    @Autowired
    private KafkaProperties kafkaProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Bean("avroConsumerFactory")
    public ConsumerFactory<String, GenericRecord> avroConsumerFactory() {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        props.put("schema.registry.url", KafkaSteps.schemaRegistryUrl());
        props.put("security.protocol", SecurityProtocol.PLAINTEXT.name());
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean("jsonConsumerFactory")
    public ConsumerFactory<String, Object> jsonConsumerFactory() {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        JsonDeserializer<Object> valueDeserializer = new JsonDeserializer<>(objectMapper);
        valueDeserializer.addTrustedPackages("*");
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
    }
}
