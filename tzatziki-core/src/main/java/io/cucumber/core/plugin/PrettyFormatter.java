package io.cucumber.core.plugin;

import io.cucumber.core.exception.CucumberException;
import io.cucumber.core.gherkin.DataTableArgument;
import io.cucumber.core.gherkin.DocStringArgument;
import io.cucumber.datatable.DataTable;
import io.cucumber.datatable.DataTableFormatter;
import io.cucumber.plugin.ColorAware;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static io.cucumber.core.exception.ExceptionUtils.printStackTrace;
import static io.cucumber.core.plugin.Formats.ansi;
import static io.cucumber.core.plugin.Formats.monochrome;
import static java.lang.Math.max;
import static java.util.Collections.emptyList;
import static java.util.Locale.ROOT;
import static java.util.Optional.ofNullable;

public final class PrettyFormatter implements ConcurrentEventListener, ColorAware {

    private static final String SCENARIO_INDENT = "";
    private static final String STEP_INDENT = "  ";
    private static final String STEP_SCENARIO_INDENT = "    ";

    private final Map<UUID, Integer> commentStartIndex = new HashMap<>();

    // ↓ this block will hold the parsed nodes to be able to get the examples
    private final Map<URI, Collection<Node>> parsedTestSources = new HashMap<>();
    private final ThreadLocal<List<Node>> currentStack = ThreadLocal.withInitial(ArrayList::new);
    // ↑

    private final UTF8PrintWriter out;
    private Formats formats = ansi();

    public PrettyFormatter(OutputStream out) {
        this.out = new UTF8PrintWriter(out);
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestCaseStarted.class, this::handleTestCaseStarted);
        publisher.registerHandlerFor(TestStepFinished.class, this::handleTestStepFinished);
        publisher.registerHandlerFor(WriteEvent.class, this::handleWrite);
        publisher.registerHandlerFor(EmbedEvent.class, this::handleEmbed);
        publisher.registerHandlerFor(TestRunFinished.class, this::handleTestRunFinished);
        publisher.registerHandlerFor(TestSourceParsed.class, this::handleTestSourceParsed); // add to hook on the TestSourceParsed event
    }

    private void handleTestSourceParsed(TestSourceParsed event) {
        parsedTestSources.put(event.getUri(), event.getNodes());
    }

    private void handleTestCaseStarted(TestCaseStarted event) {
        out.println();
        preCalculateLocationIndent(event);
        printTags(event);
        printScenarioDefinition(event);

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

    private void handleTestStepFinished(TestStepFinished event) {
        printStep(event);
        printError(event);
    }

    private void handleWrite(WriteEvent event) {
        out.println();
        printText(event);
        out.println();

    }

    private void handleEmbed(EmbedEvent event) {
        out.println();
        printEmbedding(event);
        out.println();

    }

    private void handleTestRunFinished(TestRunFinished event) {
        printError(event);
        out.close();
    }

    private void preCalculateLocationIndent(TestCaseStarted event) {
        TestCase testCase = event.getTestCase();
        Integer longestStep = testCase.getTestSteps().stream()
                .filter(PickleStepTestStep.class::isInstance)
                .map(PickleStepTestStep.class::cast)
                .map(PickleStepTestStep::getStep)
                .map(step -> formatPlainStep(step.getKeyword(), step.getText()).length())
                .max(Comparator.naturalOrder())
                .orElse(0);

        int scenarioLength = formatScenarioDefinition(testCase).length();
        commentStartIndex.put(testCase.getId(), max(longestStep, scenarioLength) + 1);
    }

    private void printTags(TestCaseStarted event) {
        List<String> tags = event.getTestCase().getTags();
        if (!tags.isEmpty()) {
            out.println(PrettyFormatter.SCENARIO_INDENT + String.join(" ", tags));
        }
    }

    private void printScenarioDefinition(TestCaseStarted event) {
        TestCase testCase = event.getTestCase();
        String definitionText = formatScenarioDefinition(testCase);
        String path = relativize(testCase.getUri()).getSchemeSpecificPart();
        String locationIndent = calculateLocationIndent(event.getTestCase(), SCENARIO_INDENT + definitionText);
        out.println(SCENARIO_INDENT + definitionText + locationIndent
                + formatLocation(path + ":" + testCase.getLocation().getLine()));
    }

    private void printStep(TestStepFinished event) {
        if (event.getTestStep() instanceof PickleStepTestStep) {
            PickleStepTestStep testStep = (PickleStepTestStep) event.getTestStep();
            String keyword = testStep.getStep().getKeyword();
            String stepText = testStep.getStep().getText();
            String status = event.getResult().getStatus().name().toLowerCase(ROOT);
            String formattedStepText = formatStepText(keyword, stepText, formats.get(status),
                formats.get(status + "_arg"), testStep.getDefinitionArgument());
            String locationComment = formatLocationComment(event, testStep, keyword, stepText);
            out.println(STEP_INDENT + formattedStepText + locationComment);
            StepArgument stepArgument = testStep.getStep().getArgument();
            if (DataTableArgument.class.isInstance(stepArgument)) {
                DataTableFormatter tableFormatter = DataTableFormatter
                        .builder()
                        .prefixRow(STEP_SCENARIO_INDENT)
                        .escapeDelimiters(false)
                        .build();
                DataTableArgument dataTableArgument = (DataTableArgument) stepArgument;
                try {
                    tableFormatter.formatTo(DataTable.create(dataTableArgument.cells()), out);
                } catch (IOException e) {
                    throw new CucumberException(e);
                }
            // ↓ print out the step docString
            } else if (stepArgument instanceof DocStringArgument) {
                DocStringArgument docStringArgument = (DocStringArgument) stepArgument;
                out.println("    \"\"\""+ ofNullable(docStringArgument.getContentType()).orElse(""));
                out.println(docStringArgument.getContent().replaceAll("(?m)^", "    ").replaceAll("(?m)\"\"\"\"\"", "\\\\\"\\\\\"\\\\\""));
                out.println("    \"\"\"");
            // ↑
            }
        }
    }

    private String formatLocationComment(
            TestStepFinished event, PickleStepTestStep testStep, String keyword, String stepText
    ) {
        String codeLocation = testStep.getCodeLocation();
        if (codeLocation == null) {
            return "";
        }
        String locationIndent = calculateLocationIndent(event.getTestCase(), formatPlainStep(keyword, stepText));
        return locationIndent + formatLocation(codeLocation);
    }

    private void printError(TestStepFinished event) {
        Result result = event.getResult();
        printError(result);
    }

    private void printError(TestRunFinished event) {
        Result result = event.getResult();
        printError(result);
    }

    private void printError(Result result) {
        Throwable error = result.getError();
        if (error != null) {
            String name = result.getStatus().name().toLowerCase(ROOT);
            Format format = formats.get(name);
            String text = printStackTrace(error);
            out.println("      " + format.text(text));
        }
    }

    private void printText(WriteEvent event) {
        // Prevent interleaving when multiple threads write to System.out
        StringBuilder builder = new StringBuilder();
        try (BufferedReader lines = new BufferedReader(new StringReader(event.getText()))) {
            String line;
            while ((line = lines.readLine()) != null) {
                builder.append(STEP_SCENARIO_INDENT)
                        .append(line)
                        // Add system line separator - \n won't do it!
                        .append(System.lineSeparator());
            }
        } catch (IOException e) {
            throw new CucumberException(e);
        }
        out.append(builder);
    }

    private void printEmbedding(EmbedEvent event) {
        String line = "Embedding " + event.getName() + " [" + event.getMediaType() + " " + event.getData().length
                + " bytes]";
        out.println(STEP_SCENARIO_INDENT + line);
    }

    private String formatPlainStep(String keyword, String stepText) {
        return STEP_INDENT + keyword + stepText;
    }

    private String formatScenarioDefinition(TestCase testCase) {
        return testCase.getKeyword() + ": " + testCase.getName();
    }

    static URI relativize(URI uri) {
        if (!"file".equals(uri.getScheme())) {
            return uri;
        }
        if (!uri.isAbsolute()) {
            return uri;
        }

        try {
            URI root = new File("").toURI();
            URI relative = root.relativize(uri);
            // Scheme is lost by relativize
            return new URI("file", relative.getSchemeSpecificPart(), relative.getFragment());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private String calculateLocationIndent(TestCase testStep, String prefix) {
        Integer commentStartAt = commentStartIndex.getOrDefault(testStep.getId(), 0);
        int padding = commentStartAt - prefix.length();

        if (padding < 0) {
            return " ";
        }

        StringBuilder builder = new StringBuilder(padding);
        for (int i = 0; i < padding; i++) {
            builder.append(" ");
        }
        return builder.toString();
    }

    private String formatLocation(String location) {
        return formats.get("comment").text("# " + location);
    }

    String formatStepText(
            String keyword, String stepText, Format textFormat, Format argFormat, List<Argument> arguments
    ) {
        int beginIndex = 0;
        StringBuilder result = new StringBuilder(textFormat.text(keyword));
        for (Argument argument : arguments) {
            // can be null if the argument is missing.
            if (argument.getValue() != null) {
                int argumentOffset = argument.getStart();
                // a nested argument starts before the enclosing argument ends;
                // ignore it when formatting
                if (argumentOffset < beginIndex) {
                    continue;
                }
                String text = stepText.substring(beginIndex, argumentOffset);
                result.append(textFormat.text(text));
            }
            // val can be null if the argument isn't there, for example
            // @And("(it )?has something")
            if (argument.getValue() != null) {
                String text = stepText.substring(argument.getStart(), argument.getEnd());
                result.append(argFormat.text(text));
                // set beginIndex to end of argument
                beginIndex = argument.getEnd();
            }
        }
        if (beginIndex != stepText.length()) {
            String text = stepText.substring(beginIndex);
            result.append(textFormat.text(text));
        }
        return result.toString();
    }

    @Override
    public void setMonochrome(boolean monochrome) {
        formats = monochrome ? monochrome() : ansi();
    }

}
