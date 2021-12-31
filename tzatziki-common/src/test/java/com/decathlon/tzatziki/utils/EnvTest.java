package com.decathlon.tzatziki.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnvTest {

    @Test
    void export() {
        assertThat(Env.export("TEST", "value")).isNull();
        assertThat(System.getenv("TEST")).isEqualTo("value");
    }
}
