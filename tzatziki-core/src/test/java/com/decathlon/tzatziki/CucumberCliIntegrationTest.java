package com.decathlon.tzatziki;

import com.decathlon.tzatziki.utils.Mapper;
import io.cucumber.core.cli.Main;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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
    @SuppressWarnings({"unchecked", "rawtypes"})
    void jsonReportOmitsStepsSkippedByGuard(@TempDir Path tempDir) throws IOException {
        Path jsonReport = tempDir.resolve("cucumber.json");

        assertThat(Main.run(
                "--glue", GLUE,
                "--name", "^test 1 and 2$",
                "--plugin", "json:" + jsonReport,
                FEATURE)).isZero();

        List<Map> features = Mapper.readAsAListOf(Files.readString(jsonReport), Map.class);
        List<String> stepNames = features.stream()
                .flatMap(feature -> ((List<Map<String, Object>>) feature.get("elements")).stream())
                .flatMap(element -> ((List<Map<String, Object>>) element.get("steps")).stream())
                .map(step -> (String) step.get("name"))
                .toList();
        assertThat(stepNames)
                .contains("that map is a Map:", "map.property is equal to 1")
                .doesNotContain("if map.property == 4 => map.property is equal to 4");
    }
}
