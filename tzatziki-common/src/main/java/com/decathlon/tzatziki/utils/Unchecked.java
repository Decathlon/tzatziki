package com.decathlon.tzatziki.utils;

import java.util.function.UnaryOperator;

@SuppressWarnings({"unchecked","java:S112"})
public final class Unchecked {

    private Unchecked() {
    }

    public static <T extends Throwable, E> E rethrow(Throwable throwable) throws T {
        return rethrow(throwable, UnaryOperator.identity());
    }

    public static <T extends Throwable, E> E rethrow(Throwable throwable, UnaryOperator<Throwable> rethrower) throws T {
        throw (T) rethrower.apply(throwable);
    }

    public static <R> R unchecked(Supplier<R> supplier) {
        return unchecked(supplier, UnaryOperator.identity());
    }

    public static <R> R unchecked(Supplier<R> supplier, UnaryOperator<Throwable> rethrower) {
        try {
            return supplier.get();
        } catch (Throwable t) {
            rethrow(t, rethrower);
        }
        throw new RuntimeException("unreachable");
    }

    public static void unchecked(Runnable runnable) {
        unchecked(runnable, UnaryOperator.identity());
    }

    public static void unchecked(Runnable runnable, UnaryOperator<Throwable> rethrower) {
        try {
            runnable.run();
        } catch (Throwable t) {
            rethrow(t, rethrower);
        }
    }

    @FunctionalInterface
    public interface Runnable {

        void run() throws Throwable;
    }

    @FunctionalInterface
    public interface Supplier<R> {

        R get() throws Throwable;
    }
}