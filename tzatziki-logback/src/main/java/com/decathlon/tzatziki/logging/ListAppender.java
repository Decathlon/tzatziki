package com.decathlon.tzatziki.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Credits: https://github.com/mimfgg/semla
 */
public class ListAppender extends OutputStreamAppender<ILoggingEvent> {

    private final List<String> logLines = Collections.synchronizedList(new ArrayList<>());

    public List<String> logLines() {
        return logLines;
    }

    @Override
    protected void append(ILoggingEvent iLoggingEvent) {
        logLines.add(new String(encoder.encode(iLoggingEvent)).trim());
    }
}
