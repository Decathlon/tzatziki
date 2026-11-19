package com.decathlon.tzatziki;

import org.testng.ITestContext;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestNGDryRunTest {

    @Test
    public void dryRunDataProviderDiscoversScenarios(ITestContext context) {
        assertThat(new TestNGTest().scenarios(context)).isNotEmpty();
    }
}
