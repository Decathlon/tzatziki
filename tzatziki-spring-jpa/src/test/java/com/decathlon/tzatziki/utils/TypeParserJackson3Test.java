package com.decathlon.tzatziki.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TypeParserJackson3Test {

    @Test
    void resolvesDuplicateJacksonTypesUsingTheActiveMapper() {
        assertThat(TypeParser.parse("UnrecognizedPropertyException"))
                .isEqualTo(tools.jackson.databind.exc.UnrecognizedPropertyException.class);
    }
}
