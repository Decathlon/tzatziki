package com.decathlon.tzatziki.utils;

import org.assertj.core.api.Assertions;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

class MatchersTest {

    @Test
    void equalsInOrder() {
        MatcherAssert.assertThat("[1,2,3]", Matchers.equalsInOrder("[1,2,3]"));
    }

    @Test
    void equalsInAnyOrder() {
        MatcherAssert.assertThat("[1,2,3]", Matchers.equalsInAnyOrder("[3,2,1]"));
    }

    @Test
    void containsOnly() {
        MatcherAssert.assertThat("[1,2,3]", Matchers.containsOnly("[2,1,3]"));
    }

    @Test
    void containsOnlyInOrder() {
        MatcherAssert.assertThat("[1,2,3]", Matchers.containsOnlyInOrder("[1,2,3]"));
    }

    @Test
    void contains() {
        MatcherAssert.assertThat("[1,2,3]", Matchers.contains("[2,1]"));
    }

    @Test
    void containsInOrder() {
        MatcherAssert.assertThat("[1,2,3]", Matchers.containsInOrder("[1,2]"));
    }

    @Test
    void failing() {
        Assertions.assertThatThrownBy(() -> MatcherAssert.assertThat("[1,2,3]", Matchers.contains("[4,1]"))).isInstanceOf(AssertionError.class);
    }
}
