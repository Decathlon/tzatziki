package io.cucumber.core.runtime;

import io.cucumber.core.plugin.Options;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.*;

import java.net.URI;
import java.util.*;
import java.util.function.Predicate;

import static java.util.Collections.*;
import static java.util.Comparator.comparing;

public final class ExitStatus implements ConcurrentEventListener {

    private static final byte DEFAULT = 0x0;
    private static final byte ERRORS = 0x1;

    private final List<Result> results = new ArrayList<>();
    private final Options options;

    private final EventHandler<TestCaseFinished> testCaseFinishedHandler = event -> results.add(event.getResult());

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

    public ExitStatus(Options options) {
        this.options = options;
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestCaseStarted.class, this::handleTestCaseStarted); // add to hook on the TestCaseStarted event
        publisher.registerHandlerFor(TestSourceParsed.class, this::handleTestSourceParsed); // add to hook on the TestSourceParsed event
        publisher.registerHandlerFor(TestCaseFinished.class, testCaseFinishedHandler);
    }

    byte exitStatus() {
        return isSuccess() ? DEFAULT : ERRORS;
    }

    boolean isSuccess() {
        if (results.isEmpty()) {
            return true;
        }

        if (options.isWip()) {
            Result leastSeverResult = min(results, comparing(Result::getStatus));
            return !leastSeverResult.getStatus().is(Status.PASSED);
        } else {
            Result mostSevereResult = max(results, comparing(Result::getStatus));
            return mostSevereResult.getStatus().isOk();
        }
    }

    Status getStatus() {
        if (results.isEmpty()) {
            return Status.PASSED;
        }
        Result mostSevereResult = max(results, comparing(Result::getStatus));
        return mostSevereResult.getStatus();
    }

}