package com.decathlon.tzatziki;

import org.testng.ITestContext;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Deprecated
@SuppressWarnings("deprecation")
public class TestNGDryRunTest {

    @Test
    public void dryRunDataProviderDiscoversScenarios(ITestContext context) {
        assertThat(new TestNGTest().scenarios(context)).isNotEmpty();
    }
}
