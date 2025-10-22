package io.cucumber.core.plugin;

import io.cucumber.messages.types.Envelope;
import io.cucumber.plugin.ColorAware;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.*;
import io.cucumber.prettyformatter.MessagesToSummaryWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.util.*;
import java.util.function.Predicate;

import static io.cucumber.prettyformatter.Theme.cucumber;
import static io.cucumber.prettyformatter.Theme.plain;
import static java.util.Collections.emptyList;

public final class DefaultSummaryPrinter implements ColorAware, ConcurrentEventListener {

    private final OutputStream out;
    private MessagesToSummaryWriter writer;

    // ↓ this block will hold the parsed nodes to be able to get the examples
    private final Map<URI, Collection<Node>> parsedTestSources = new HashMap<>();
    private final ThreadLocal<List<Node>> currentStack = ThreadLocal.withInitial(ArrayList::new);
    // ↑

    private void handleTestCaseStarted(TestCaseStarted event) {
        // ↓ Taken from the TeamCityPlugin to get access to the Examples
        TestCase testCase = event.getTestCase();
        URI uri = testCase.getUri();
        Location location = testCase.getLocation();
        Predicate<Node> withLocation = candidate -> location.equals(candidate.getLocation());
        this.currentStack.set(parsedTestSources.get(uri)
                .stream()
                .map(node -> node.findPathTo(withLocation))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .orElse(emptyList()));
        // ↑
    }

    private void handleTestSourceParsed(TestSourceParsed event) {
        parsedTestSources.put(event.getUri(), event.getNodes());
    }

    public DefaultSummaryPrinter() {
        this(new PrintStream(System.out) {
            @Override
            public void close() {
                // Don't close System.out
            }
        });
    }

    DefaultSummaryPrinter(OutputStream out) {
        this.out = out;
        this.writer = createBuilder().build(out);
    }

    private static MessagesToSummaryWriter.Builder createBuilder() {
        return MessagesToSummaryWriter.builder()
                .theme(cucumber());
    }

    @Override
    public void setMonochrome(boolean monochrome) {
        if (monochrome) {
            writer = createBuilder().theme(plain()).build(out);
        }
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestCaseStarted.class, this::handleTestCaseStarted); // add to hook on the TestCaseStarted event
        publisher.registerHandlerFor(TestSourceParsed.class, this::handleTestSourceParsed); // add to hook on the TestSourceParsed event
        publisher.registerHandlerFor(Envelope.class, this::write);
    }

    private void write(Envelope event) {
        try {
            writer.write(event);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        // TODO: Plugins should implement the closable interface
        // and be closed by Cucumber
        if (event.getTestRunFinished().isPresent()) {
            writer.close();
        }
    }

}