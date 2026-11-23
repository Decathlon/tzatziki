package io.cucumber.core.runtime;

import io.cucumber.core.plugin.Options;
import io.cucumber.messages.types.Envelope;
import io.cucumber.messages.types.TestRunHookFinished;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.*;
import io.cucumber.query.Query;
import io.cucumber.query.Repository;

import java.net.URI;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;

public final class ExitStatus implements ConcurrentEventListener {

    private static final byte DEFAULT = 0x0;
    private static final byte ERRORS = 0x1;
    private static final byte NO_TESTS = 0x2;

    private final List<Result> results = new ArrayList<>();
    private final Repository repository = Repository.builder().build();
    private final Query query = new Query(repository);
    private final Options options;
    private final boolean failIfNoTests;

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
        this(options, false);
    }

    ExitStatus(Options options, boolean failIfNoTests) {
        this.options = options;
        this.failIfNoTests = failIfNoTests;
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestCaseStarted.class, this::handleTestCaseStarted); // add to hook on the TestCaseStarted event
        publisher.registerHandlerFor(TestSourceParsed.class, this::handleTestSourceParsed); // add to hook on the TestSourceParsed event
        publisher.registerHandlerFor(TestCaseFinished.class, testCaseFinishedHandler);
        publisher.registerHandlerFor(Envelope.class, repository::update);
    }

    byte exitStatus() {
        if (failIfNoTests && query.findAllTestCaseStarted().isEmpty()) {
            return NO_TESTS;
        }
        return isSuccess() ? DEFAULT : ERRORS;
    }

    boolean isSuccess() {
        if (options.isWip()) {
            return getStatuses().min(Comparator.naturalOrder())
                    .map(status -> !status.is(Status.PASSED))
                    .orElse(true);
        }
        return getStatus().isOk();
    }

    Status getStatus() {
        return getStatuses().max(Comparator.naturalOrder()).orElse(Status.PASSED);
    }

    private Stream<Status> getStatuses() {
        // Scenario results preserve Tzatziki's ordering for skipped guards.
        Stream<Status> scenarios = results.stream().map(Result::getStatus);
        Stream<Status> hooks = query.findAllTestRunHookFinished().stream()
                .map(TestRunHookFinished::getResult)
                .map(result -> Status.valueOf(result.getStatus().name()));
        return Stream.concat(scenarios, hooks);
    }
}
