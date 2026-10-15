package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.app.dao.VisibilityDataSpringRepository;
import io.cucumber.java.Before;
import io.cucumber.java.en.Then;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("java:S100")
public class RepositoryUsageSteps {

    @Before
    public void resetCounters() {
        VisibilityDataSpringRepository.SAVE_ALL_CALLS.set(0);
    }

    @Then("^the VisibilityDataSpringRepository saveAll method has been called (\\d+) times?$")
    public void visibility_save_all_has_been_called(int times) {
        assertThat(VisibilityDataSpringRepository.SAVE_ALL_CALLS.get()).isEqualTo(times);
    }
}
