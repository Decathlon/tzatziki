package com.decathlon.tzatziki.utils;

import com.decathlon.tzatziki.User;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.util.Collections;
import java.util.List;

public class AssertsTest {
    @Test
    public void nonDefaultContains() {
        User actualUser = User.builder()
                .id(1)
                .name("toto")
                .friendly(true)
                .friendsId(List.of(1))
                .build();
        User expectedUser = User.builder()
                .id(1)
                .name(null)
                .friendly(false)
                .friendsId(Collections.emptyList())
                .build();

        Assertions.assertThrows(AssertionError.class, () -> Asserts.contains(actualUser, expectedUser));
    }
}
