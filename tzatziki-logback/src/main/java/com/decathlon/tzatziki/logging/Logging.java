package com.decathlon.tzatziki.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.*;
import ch.qos.logback.core.rolling.DefaultTimeBasedFileNamingAndTriggeringPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Credits: https://github.com/mimfgg/semla
 */
public final class Logging {

    private Logging() {}

    public static Configurator withLogLevel(Level level) {
        return new Configurator().withLogLevel(level);
    }

    public static Configurator withAppenderLevel(String pattern, Level level) {
        return new Configurator().withAppenderLevel(pattern, level);
    }

    public static Configurator withPattern(String pattern) {
        return new Configurator().withPattern(pattern);
    }

    public static Configurator withAppender(Appender<ILoggingEvent> appender) {
        return new Configurator().withAppender(appender);
    }

    public static Configurator configure() {
        return new Configurator();
    }

    public static void setup() {
        new Configurator().setup();
    }

    public static void setTo(Level level) {
        withLogLevel(level).setup();
    }

    public static Logger root() {
        return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }

    public static Logger logger(Class<?> clazz) {
        return (Logger) LoggerFactory.getLogger(clazz);
    }

    public static Logger logger(String name) {
        return (Logger) LoggerFactory.getLogger(name);
    }

    public static class Configurator {

        private Level level = Level.INFO;
        private String pattern = "%d{ISO8601," + TimeZone.getDefault().getID() + "} %-5p [%c{1}] %m%n";
        private final Map<String, Level> loggers = new HashMap<>();
        private boolean withConsole = true;
        private boolean withFileAppender = false;
        private int archivedFileCount = 0;
        private String logFilename = "./file.log";
        private String logFilenamePattern = "./file-%d.log.gz";
        private Appender<ILoggingEvent> appender;

        public Configurator withLogLevel(Level level) {
            this.level = level;
            return this;
        }

        public Configurator withAppenderLevel(String pattern, Level level) {
            loggers.put(pattern, level);
            return this;
        }

        public Configurator withPattern(String pattern) {
            this.pattern = pattern;
            return this;
        }

        public Configurator noConsole() {
            withConsole = false;
            return this;
        }

        public Configurator withFileAppender() {
            withFileAppender = true;
            return this;
        }

        public Configurator keep(int fileCount) {
            this.archivedFileCount = fileCount;
            return this;
        }

        public Configurator withLogFilename(String logFilename) {
            this.logFilename = logFilename;
            return this;
        }

        public Configurator withLogFilenamePattern(String logFilenamePattern) {
            this.logFilenamePattern = logFilenamePattern;
            return this;
        }

        public Configurator withAppender(Appender<ILoggingEvent> appender) {
            this.appender = appender;
            return this;
        }

        public void setup() {
//            SLF4JBridgeHandler.removeHandlersForRootLogger();
//            SLF4JBridgeHandler.install();
            Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            LoggerContext loggerContext = root.getLoggerContext();
            loggerContext.reset();

            if (appender != null) {
                appender.setName(appender.getClass().getSimpleName());
                if (appender instanceof OutputStreamAppender) {
                    addPatternLayoutEncoder(loggerContext, (OutputStreamAppender<ILoggingEvent>) appender);
                }
                appender.setContext(loggerContext);
                appender.start();
                root.addAppender(appender);
            }

            if (withConsole) {
                ConsoleAppender<ILoggingEvent> console = new ConsoleAppender<>();
                console.setName(ConsoleAppender.class.getSimpleName());
                addPatternLayoutEncoder(loggerContext, console);
                console.setContext(loggerContext);
                console.start();
                root.addAppender(console);
            }

            if (withFileAppender) {
                FileAppender<ILoggingEvent> fileAppender;

                if (archivedFileCount > 0) {
                    RollingFileAppender<ILoggingEvent> rollingFileAppender = new RollingFileAppender<>();
                    DefaultTimeBasedFileNamingAndTriggeringPolicy<ILoggingEvent> triggeringPolicy = new DefaultTimeBasedFileNamingAndTriggeringPolicy<>();
                    triggeringPolicy.setContext(loggerContext);

                    TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new TimeBasedRollingPolicy<>();
                    rollingPolicy.setContext(loggerContext);
                    rollingPolicy.setFileNamePattern(logFilenamePattern);
                    rollingPolicy.setTimeBasedFileNamingAndTriggeringPolicy(triggeringPolicy);
                    triggeringPolicy.setTimeBasedRollingPolicy(rollingPolicy);
                    rollingPolicy.setMaxHistory(archivedFileCount);

                    rollingFileAppender.setRollingPolicy(rollingPolicy);
                    rollingFileAppender.setTriggeringPolicy(triggeringPolicy);

                    rollingPolicy.setParent(rollingFileAppender);
                    rollingPolicy.start();
                    fileAppender = rollingFileAppender;
                } else {
                    fileAppender = new FileAppender<>();
                }

                fileAppender.setName(fileAppender.getClass().getSimpleName());
                fileAppender.setAppend(true);
                fileAppender.setContext(loggerContext);
                addPatternLayoutEncoder(loggerContext, fileAppender);
                fileAppender.setFile(logFilename);
                fileAppender.setPrudent(false);
                fileAppender.start();

                AsyncAppender asyncAppender = new AsyncAppender(fileAppender, 100);
                asyncAppender.start();
                root.addAppender(asyncAppender);
            }

            root.setLevel(level);

            loggers.forEach((key, value) -> ((Logger) LoggerFactory.getLogger(key)).setLevel(value));
        }

        private void addPatternLayoutEncoder(LoggerContext loggerContext, OutputStreamAppender<ILoggingEvent> appender) {
            PatternLayoutEncoder patternLayoutEncoder = new PatternLayoutEncoder();
            patternLayoutEncoder.setPattern(pattern);
            patternLayoutEncoder.setContext(loggerContext);
            patternLayoutEncoder.start();
            appender.setEncoder(patternLayoutEncoder);
        }
    }

    private static class AsyncAppender extends AppenderBase<ILoggingEvent> {

        private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();
        private final BlockingQueue<ILoggingEvent> queue;
        private final Printer printer;
        private final Appender<ILoggingEvent> delegate;

        private AsyncAppender(Appender<ILoggingEvent> delegate, int batchSize) {
            this.queue = new LinkedBlockingQueue<>();
            this.printer = new Printer(batchSize);
            this.delegate = delegate;
            setName("async-" + delegate.getName());
        }

        @Override
        public void start() {
            super.start();
            printer.setName(getName() + "-" + THREAD_COUNTER.incrementAndGet());
            printer.start();
            Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
        }

        @Override
        public void stop() {
            super.stop();
            printer.shutdown();
        }

        @Override
        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            try {
                queue.put(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private class Printer extends Thread {

            private final int batchSize;
            private volatile boolean running = true;
            private final List<ILoggingEvent> events;

            private Printer(int batchSize) {
                this.batchSize = batchSize;
                this.events = new ArrayList<>(batchSize);
                this.setDaemon(true);
            }

            @Override
            public void run() {
                try {
                    while (running) {
                        queue.drainTo(events, batchSize);
                        if (!events.isEmpty()) {
                            events.forEach(delegate::doAppend);
                        } else {
                            try {
                                TimeUnit.MILLISECONDS.sleep(10);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                        events.clear();
                    }
                } finally {
                    events.forEach(delegate::doAppend);
                    delegate.stop();
                }
            }

            public void shutdown() {
                this.running = false;
                this.interrupt();
            }
        }
    }
}
