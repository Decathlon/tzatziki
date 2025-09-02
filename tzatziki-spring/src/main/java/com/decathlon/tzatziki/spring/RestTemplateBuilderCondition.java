package com.decathlon.tzatziki.spring;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Condition that checks if RestTemplateBuilder is available in either Spring Boot 3 or Spring Boot 4 location.
 */
public class RestTemplateBuilderCondition implements Condition {

    private static final String SPRING_BOOT_3_PACKAGE = "org.springframework.boot.web.client.RestTemplateBuilder";
    private static final String SPRING_BOOT_4_PACKAGE = "org.springframework.boot.restclient.RestTemplateBuilder";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        ClassLoader classLoader = context.getClassLoader();
        if (classLoader == null) {
            classLoader = Thread.currentThread().getContextClassLoader();
        }

        // Check if RestTemplateBuilder is available in either Spring Boot 3 or Spring Boot 4 location
        return isClassPresent(SPRING_BOOT_3_PACKAGE, classLoader) ||
               isClassPresent(SPRING_BOOT_4_PACKAGE, classLoader);
    }

    private boolean isClassPresent(String className, ClassLoader classLoader) {
        try {
            Class.forName(className, false, classLoader);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
