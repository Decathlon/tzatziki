package com.decathlon.tzatziki;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.core.cli.Main;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CucumberCliIntegrationTest {

    private static final String FEATURE = "classpath:com/decathlon/tzatziki/steps/scenario-with-background.feature";
    private static final String GLUE = "com.decathlon.tzatziki.steps";

    @Test
    void exitsSuccessfullyWhenNoScenariosMatchByDefault() {
        assertThat(Main.run("--tags", "@not-present", "--glue", GLUE, FEATURE)).isZero();
    }

    @Test
    void exitsWithCodeTwoWhenNoScenariosMatchAndFailIfNoTestsIsEnabled() {
        assertThat(Main.run("--fail-if-no-tests", "--tags", "@not-present", "--glue", GLUE, FEATURE))
                .isEqualTo((byte) 2);
    }

    @Test
    void exitsSuccessfullyWhenScenariosMatchAndFailIfNoTestsIsEnabled() {
        assertThat(Main.run(
                "--fail-if-no-tests", "--glue", GLUE, "--name", "^test 1 and 2$", FEATURE))
                .isZero();
    }

    @Test
    void jsonReportOmitsStepsSkippedByGuard(@TempDir Path tempDir) throws IOException {
        Path jsonReport = tempDir.resolve("cucumber.json");

        assertThat(Main.run(
                "--glue", GLUE,
                "--name", "^test 1 and 2$",
                "--plugin", "json:" + jsonReport,
                FEATURE)).isZero();

        JsonNode report = new ObjectMapper().readTree(jsonReport.toFile());
        List<String> stepNames = new ArrayList<>();
        for (JsonNode feature : report) {
            for (JsonNode element : feature.path("elements")) {
                for (JsonNode step : element.path("steps")) {
                    stepNames.add(step.path("name").asText());
                }
            }
        }
        assertThat(stepNames)
                .contains("that map is a Map:", "map.property is equal to 1")
                .doesNotContain("if map.property == 4 => map.property is equal to 4");
    }
}
