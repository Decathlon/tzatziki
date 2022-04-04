package com.decathlon.tzatziki.utils;

import com.decathlon.tzatziki.steps.ObjectSteps;
import com.google.common.base.Splitter;
import io.cucumber.core.runner.SkipStepException;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.decathlon.tzatziki.utils.Patterns.*;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.runAsync;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

public class Guard {
    public static final String GUARD_PATTERN = "(?:if [\\S]+ .+ =>|" +
        "else|otherwise|" +
        "it is not true that|" +
        "after \\d+ms|" +
        "within \\d+ms|" +
        "during \\d+ms|" +
        "an? (?:" + VARIABLE_PATTERN + " )?" + TYPE_PATTERN + " is thrown when)";
    public static final String GUARD = "(?:(" + GUARD_PATTERN + "(?: " + GUARD_PATTERN + ")*) )?";
    public static final String MULTI_GUARD_CAPTURE = "(?=(" + GUARD_PATTERN + "))";
    public static final Pattern PATTERN = Pattern.compile("([\\S]+) (.+)");
    public static boolean latestEvaluatedConditionResult = true;

    private Guard next;

    public void in(ObjectSteps objects, Runnable stepToRun) {
        if (next != null) {
            next.in(objects, stepToRun);
        }else {
            stepToRun.run();
        }
    }

    public static Guard parse(String value) {
        if (value != null) {
            final Matcher guardMatcher = Pattern.compile(MULTI_GUARD_CAPTURE).matcher(value);

            if (!guardMatcher.find()) return always();
            final Guard firstGuard = extractGuard(guardMatcher.group(1));

            Guard currentGuard = firstGuard;
            while (guardMatcher.find()) {
                final Guard nextGuard = extractGuard(guardMatcher.group(1));
                currentGuard.next = nextGuard;
                currentGuard = nextGuard;
            }

            return firstGuard;
        } else {
            return always();
        }
    }

    private static Guard extractGuard(String value) {
        if (value.startsWith("it is not true that")) {
            return invert();
        } else if(value.startsWith("else") || value.startsWith("otherwise")){
            return elseCondition();
        }else if (value.startsWith("after ")) {
            return async(extractInt(value, "after (\\d+)ms"));
        } else if (value.startsWith("within ")) {
            return within(extractInt(value, "within (\\d+)ms"));
        } else if (value.startsWith("during ")) {
            return during(extractInt(value, "during (\\d+)ms"));
        } else if (value.endsWith("is thrown when")) {
            Pattern pattern = Pattern.compile("an? (?:" + VARIABLE + " )?" + TYPE + " is thrown when");
            Matcher matcher = pattern.matcher(value);
            assertThat(matcher.matches()).isTrue();
            String name = ofNullable(matcher.group(1)).orElse("_exception");
            Type exceptionType = TypeParser.parse(matcher.group(2));
            return expectException(name, Types.rawTypeOf(exceptionType));
        } else {
            return skipOnCondition(value.replaceFirst("^if ", "").replaceAll(" =>$", ""));
        }
    }

    private static Guard elseCondition() {
        return new Guard() {
            @Override
            public void in(ObjectSteps objects, Runnable stepToRun) {
                if (latestEvaluatedConditionResult) {
                    throw new SkipStepException();
                }
                super.in(objects, stepToRun);
            }
        };
    }

    @NotNull
    private static String extractString(String value, String s) {
        return value.replaceFirst(s, "$1");
    }

    private static int extractInt(String value, String s) {
        return Integer.parseInt(value.replaceFirst(s, "$1"));
    }

    public static Guard always() {
        return new Guard();
    }

    private static Guard skipOnCondition(String value) {
        return new Guard() {
            @Override
            public void in(ObjectSteps objects, Runnable stepToRun) {
                Splitter.on("&&").splitToList(value).forEach(token -> {
                    Matcher matcher = PATTERN.matcher(token.trim());
                    if (matcher.matches()) {
                        try {
                            Asserts.equalsInAnyOrder(objects.getOrSelf(matcher.group(1)),
                                "?" + objects.resolve(matcher.group(2)));
                        } catch (AssertionError e) {
                            latestEvaluatedConditionResult = false;
                            throw new SkipStepException();
                        }
                    }
                });
                latestEvaluatedConditionResult = true;
                super.in(objects, stepToRun);
            }
        };
    }

    private static Guard invert() {
        return new Guard() {
            @Override
            public void in(ObjectSteps objects, Runnable stepToRun) {
                boolean testPassed = false;
                Duration defaultTimeOut = Asserts.defaultTimeOut;
                try {
                    Asserts.defaultTimeOut = Duration.of(200, MILLIS);
                    super.in(objects, stepToRun);
                    testPassed = true;
                } catch (Throwable e) {
                    // The test failed
                } finally {
                    Asserts.defaultTimeOut = defaultTimeOut;
                }
                if (testPassed) {
                    fail("This test was expected to fail.");
                }
            }
        };
    }

    private static Guard async(int delay) {
        return new Guard() {
            @Override
            public void in(ObjectSteps objects, Runnable stepToRun) {
                runAsync(() -> {
                    try {
                        TimeUnit.MILLISECONDS.sleep(delay);
                        super.in(objects, stepToRun);
                    } catch (InterruptedException e) {
                        // Restore interrupted state...
                        Thread.currentThread().interrupt();
                    } finally {
                        LoggerFactory.getLogger(Guard.class).debug("ran async step {}", stepToRun);
                    }
                });
            }
        };
    }

    private static Guard within(int delay) {
        return new Guard() {
            @Override
            public void in(ObjectSteps objects, Runnable stepToRun) {
                Asserts.awaitUntilAsserted(() -> super.in(objects, stepToRun), Duration.ofMillis(delay));
            }
        };
    }

    private static Guard during(int delay) {
        return new Guard() {
            @Override
            public void in(ObjectSteps objects, Runnable stepToRun) {
                Asserts.awaitDuring(() -> super.in(objects, stepToRun), Duration.ofMillis(delay));
            }
        };
    }

    private static <T extends Throwable> Guard expectException(String name, Class<T> expectedException) {
        return new Guard() {
            @Override
            public void in(ObjectSteps objects, Runnable stepToRun) {
                try {
                    super.in(objects, stepToRun);
                } catch (Throwable t) {
                    if (Types.isAssignableTo(t.getClass(), expectedException)) {
                        objects.add(name, t);
                        return;
                    }
                }
                throw new AssertionError("was expecting %s to be thrown ... ".formatted(expectedException.getName()));
            }
        };
    }
}
