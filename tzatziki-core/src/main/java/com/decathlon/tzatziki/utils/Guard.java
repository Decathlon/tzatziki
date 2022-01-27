package com.decathlon.tzatziki.utils;

import com.decathlon.tzatziki.steps.ObjectSteps;
import com.google.common.base.Splitter;
import io.cucumber.core.runner.SkipStepException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.time.temporal.ChronoUnit.MILLIS;
import static java.util.concurrent.CompletableFuture.runAsync;
import static org.junit.jupiter.api.Assertions.fail;

@FunctionalInterface
public interface Guard {

    String GUARD_PATTERN = "((?:if [\\S]+ .+ =>)|it is not true that|after \\d+ms|within \\d+ms|during \\d+ms|an? \\S+ is thrown when)";
    String GUARD = "(?:" + GUARD_PATTERN + " )?";
    Pattern PATTERN = Pattern.compile("([\\S]+) (.+)");

    void in(ObjectSteps objects, Runnable stepToRun);

    static Guard parse(String value) {
        if (value != null) {
            if ((value.equals("it is not true that"))) {
                return invert();
            } else if (value.startsWith("after ")) {
                return async(extractInt(value, "after (\\d+)ms"));
            } else if (value.startsWith("within ")) {
                return within(extractInt(value, "within (\\d+)ms"));
            } else if (value.startsWith("during ")) {
                return during(extractInt(value, "during (\\d+)ms"));
            } else if (value.matches("^an? \\S+ is thrown when")){
                final Type exceptionType = TypeParser.parse(extractString(value, "an? (\\S+) is thrown when"));
                return expectException(Types.rawTypeOf(exceptionType));
            }
            return skipOnCondition(value.replaceFirst("^if ", "").replaceAll(" =>$", ""));
        } else {
            return always();
        }
    }

    @NotNull
    private static String extractString(String value, String s) {
        return value.replaceFirst(s, "$1");
    }

    static int extractInt(String value, String s) {
        return Integer.parseInt(value.replaceFirst(s, "$1"));
    }

    static Guard always() {
        return (objects, stepToRun) -> stepToRun.run();
    }

    static Guard skipOnCondition(String value) {
        return (objects, stepToRun) -> {
            Splitter.on("&&").splitToList(value).forEach(token -> {
                Matcher matcher = PATTERN.matcher(token.trim());
                if (matcher.matches()) {
                    try {
                        Asserts.equalsInAnyOrder(objects.getOrSelf(matcher.group(1)),
                                "?" + objects.resolve(matcher.group(2)));
                    } catch (AssertionError e) {
                        throw new SkipStepException();
                    }
                }
            });
            stepToRun.run();
        };
    }

    static Guard invert() {
        return (objects, stepToRun) -> {
            boolean testPassed = false;
            Duration defaultTimeOut = Asserts.defaultTimeOut;
            try {
                Asserts.defaultTimeOut = Duration.of(200, MILLIS);
                stepToRun.run();
                testPassed = true;
            } catch (Throwable e) {
                // The test failed
            } finally {
                Asserts.defaultTimeOut = defaultTimeOut;
            }
            if (testPassed) {
                fail("This test was expected to fail.");
            }
        };
    }

    static Guard async(int delay) {
        return (objects, stepToRun) -> runAsync(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(delay);
                stepToRun.run();
            } catch (InterruptedException e) {
                // Restore interrupted state...
                Thread.currentThread().interrupt();
            } finally {
                LoggerFactory.getLogger(Guard.class).debug("ran async step {}", stepToRun);
            }
        });
    }

    static Guard within(int delay) {
        return (objects, stepToRun) -> Asserts.awaitUntilAsserted(stepToRun::run, Duration.ofMillis(delay));
    }

    static Guard during(int delay) {
        return (objects, stepToRun) -> Asserts.awaitDuring(stepToRun::run, Duration.ofMillis(delay));
    }

    static <T extends Throwable> Guard expectException(Class<T> expectedException) {
        return (objects, stepToRun) -> Asserts.threwException(stepToRun::run, expectedException);
    }
}
