package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.app.beans.NonSnakeCasePojo;
import com.decathlon.tzatziki.utils.Jackson3Mapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class Jackson3NamingStrategyConfigurerTest {

    @Test
    void copiesPropertyNamingStrategyFromSpringJackson3Mapper() {
        ObjectMapper springMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean(ObjectMapper.class, () -> springMapper);
            context.refresh();

            boolean applied = Jackson3NamingStrategyConfigurer.copyFrom(context);

            assertThat(applied).isTrue();

            NonSnakeCasePojo pojo = new NonSnakeCasePojo();
            pojo.setNonSnakeCaseField("value");

            assertThat(new Jackson3Mapper().toJson(pojo)).contains("non_snake_case_field");
        }
    }

    @Test
    void returnsFalseWhenNoJackson3MapperBeanIsAvailable() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.refresh();

            assertThat(Jackson3NamingStrategyConfigurer.copyFrom(context)).isFalse();
        }
    }
}
