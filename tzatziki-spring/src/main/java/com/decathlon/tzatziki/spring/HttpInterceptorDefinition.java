package com.decathlon.tzatziki.spring;

public interface HttpInterceptorDefinition<T> {
    T rewrite(T webClient);
}
