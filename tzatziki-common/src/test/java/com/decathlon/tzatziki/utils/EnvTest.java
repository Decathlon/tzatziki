package com.decathlon.tzatziki.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

class EnvTest {

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void export() {
        assertThat(Env.export("TEST", "value")).isNull();
        assertThat(System.getenv("TEST")).isEqualTo("value");
    }
}
