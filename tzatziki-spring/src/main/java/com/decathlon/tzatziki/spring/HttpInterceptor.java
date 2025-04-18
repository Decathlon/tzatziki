package com.decathlon.tzatziki.spring;

import com.decathlon.tzatziki.utils.HttpUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

@ConditionalOnWebApplication
@Aspect
@Component
@Slf4j
public class HttpInterceptor {

    private static boolean enabled = true;

    public static void enable() {
        enabled = true;
    }

    public static void disable() {
        enabled = false;
    }

    @Autowired(required = false)
    private List<HttpInterceptorDefinition> httpInterceptorDefinitions;

    @Around("@annotation(org.springframework.context.annotation.Bean) && !within(is(FinalType))")
    public Object beanCreation(ProceedingJoinPoint joinPoint) throws Throwable {
        Object bean = joinPoint.proceed();
        if (enabled && bean != null && httpInterceptorDefinitions != null) {
            return httpInterceptorDefinitions.stream()
                    .filter(httpInterceptorDefinition -> isBeanMatchingDefinition(bean, httpInterceptorDefinition)).findFirst()
                    .map(httpInterceptorDefinition -> httpInterceptorDefinition.rewrite(bean)).orElse(bean);

        }
        return bean;
    }

    @NotNull
    static URI remap(URI uri) throws URISyntaxException {
        if (enabled) {
            return new URI(HttpUtils.target(uri.toString()));
        }
        return uri;
    }

    private boolean isBeanMatchingDefinition(Object bean, HttpInterceptorDefinition<?> definition) {
        Class<?> resolvedType = ResolvableType.forClass(definition.getClass()).getInterfaces()[0].getGeneric(0).resolve();
        return resolvedType != null && resolvedType.isInstance(bean);
    }
}