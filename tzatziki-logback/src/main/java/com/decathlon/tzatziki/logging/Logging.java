package com.decathlon.tzatziki.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.OutputStreamAppender;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * Credits: https://github.com/mimfgg/semla
 */
public final class Logging {

    private Logging() {}

    public static Configurator withLogLevel(Level level) {
        return new Configurator().withLogLevel(level);
    }

    public static class Configurator {

        private Level level = Level.INFO;
        private String pattern = "%d{ISO8601," + TimeZone.getDefault().getID() + "} %-5p [%c{1}] %m%n";
        private final Map<String, Level> loggers = new HashMap<>();
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

        public Configurator withAppender(Appender<ILoggingEvent> appender) {
            this.appender = appender;
            return this;
        }

        public void setup() {
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

            ConsoleAppender<ILoggingEvent> console = new ConsoleAppender<>();
            console.setName(ConsoleAppender.class.getSimpleName());
            addPatternLayoutEncoder(loggerContext, console);
            console.setContext(loggerContext);
            console.start();
            root.addAppender(console);

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

}
