package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.Jackson3Mapper;
import org.springframework.context.ApplicationContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategy;

/**
 * Jackson 3 ({@code tools.jackson}) configurer copying the property naming strategy from the Spring
 * {@link ObjectMapper} bean onto {@code Jackson3Mapper}. Only referenced/loaded when
 * {@code Jackson3Mapper} is the active delegate.
 */
final class Jackson3NamingStrategyConfigurer {

    private Jackson3NamingStrategyConfigurer() {
    }

    /**
     * @return {@code true} if a Spring Jackson 3 ObjectMapper was found and its naming strategy applied.
     */
    static boolean copyFrom(ApplicationContext applicationContext) {
        ObjectMapper springMapper = applicationContext.getBeanProvider(ObjectMapper.class).getIfAvailable();
        if (springMapper == null) {
            return false;
        }
        PropertyNamingStrategy namingStrategy = springMapper.serializationConfig().getPropertyNamingStrategy();
        Jackson3Mapper.with(mapper -> mapper.rebuild().propertyNamingStrategy(namingStrategy).build());
        return true;
    }
}
