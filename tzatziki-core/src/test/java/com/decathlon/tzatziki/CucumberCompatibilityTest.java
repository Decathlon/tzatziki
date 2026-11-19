package com.decathlon.tzatziki;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.core.cli.Main;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CucumberCompatibilityTest {

    private static final String FEATURE = "classpath:com/decathlon/tzatziki/steps/scenario-with-background.feature";
    private static final String GLUE = "com.decathlon.tzatziki.steps";

    @Test
    void cliCanFailWhenNoScenariosAreRun() {
        assertThat(Main.run("--tags", "@not-present", "--glue", GLUE, FEATURE)).isZero();
        assertThat(Main.run("--fail-if-no-tests", "--tags", "@not-present", "--glue", GLUE, FEATURE))
                .isEqualTo((byte) 2);
    }

    @Test
    void cliPreservesGuardsExamplesAndReports(@TempDir Path tempDir) throws IOException {
        Path jsonReport = tempDir.resolve("cucumber.json");
        Path messageReport = tempDir.resolve("cucumber.ndjson");

        assertThat(Main.run(
                "--fail-if-no-tests",
                "--glue", GLUE,
                "--name", "^test 1 and 2$",
                "--plugin", "json:" + jsonReport,
                "--plugin", "message:" + messageReport,
                FEATURE)).isZero();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode report = mapper.readTree(jsonReport.toFile());
        List<JsonNode> scenarios = new ArrayList<>();
        for (JsonNode feature : report) {
            for (JsonNode element : feature.path("elements")) {
                if ("scenario".equals(element.path("type").asText())) {
                    scenarios.add(element);
                }
            }
        }
        assertThat(scenarios).hasSize(2);
        assertThat(report.toString())
                .contains("map.property is equal to 1", "map.property is equal to 2")
                .doesNotContain("if map.property == 4");

        Set<String> hookNames = new HashSet<>();
        for (String line : Files.readAllLines(messageReport)) {
            JsonNode name = mapper.readTree(line).path("hook").path("name");
            if (name.isTextual()) {
                hookNames.add(name.asText());
            }
        }
        assertThat(hookNames).contains(
                "Initialize Tzatziki scenario context",
                "Await Tzatziki asynchronous steps");
    }
}
