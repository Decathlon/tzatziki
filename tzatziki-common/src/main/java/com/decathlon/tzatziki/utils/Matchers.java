package com.decathlon.tzatziki.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

import java.util.function.Consumer;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Matchers {

    private static class ThrowableMatcher<E> extends BaseMatcher<E> {

        private final Consumer<Object> assertFunction;
        private Throwable throwable;

        protected ThrowableMatcher(Consumer<Object> assertFunction) {
            this.assertFunction = assertFunction;
        }

        @Override
        public boolean matches(Object actual) {
            try {
                assertFunction.accept(actual);
            } catch (Throwable throwable) {
                this.throwable = throwable;
                return false;
            }
            return true;
        }

        @Override
        public void describeTo(Description description) {
            if (throwable != null) {
                description.appendText(throwable.getMessage());
            }
        }
    }

    public static Matcher<Object> equalsInOrder(String expected) {
        return new ThrowableMatcher<>(actual -> Asserts.equalsInOrder(actual, expected));
    }

    public static Matcher<Object> equalsInAnyOrder(String expected) {
        return new ThrowableMatcher<>(actual -> Asserts.equalsInAnyOrder(actual, expected));
    }

    public static Matcher<Object> containsOnly(String expected) {
        return new ThrowableMatcher<>(actual -> Asserts.containsOnly(actual, expected));
    }

    public static Matcher<Object> containsOnlyInOrder(String expected) {
        return new ThrowableMatcher<>(actual -> Asserts.containsOnlyInOrder(actual, expected));
    }

    public static Matcher<Object> contains(String expected) {
        return new ThrowableMatcher<>(actual -> Asserts.contains(actual, expected));
    }

    public static Matcher<Object> containsInOrder(String expected) {
        return new ThrowableMatcher<>(actual -> Asserts.containsInOrder(actual, expected));
    }
}
