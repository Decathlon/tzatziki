package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.Comparison;
import com.decathlon.tzatziki.utils.Guard;
import com.decathlon.tzatziki.utils.InsertionMode;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

/**
 * Simulates downstream glue code still relying on the pre-split {@link SpringJPASteps} Java API.
 */
@SuppressWarnings({"java:S100", "removal"})
public class LegacyApiSteps {

    private final SpringJPASteps springJPASteps;

    public LegacyApiSteps(SpringJPASteps springJPASteps) {
        this.springJPASteps = springJPASteps;
    }

    @Given("^the legacy API inserts into the ([^ ]+) table( only)?:$")
    public void legacy_insert(String table, String only, String content) {
        InsertionMode mode = only != null ? InsertionMode.ONLY : InsertionMode.AT_LEAST;
        springJPASteps.the_repository_will_contain(Guard.always(), springJPASteps.getRepositoryForTable(table), mode, content);
    }

    @Then("^the legacy API asserts that the ([^ ]+) table contains only:$")
    public void legacy_assert(String table, String content) {
        springJPASteps.the_repository_contains(Guard.always(), springJPASteps.getRepositoryForTable(table), Comparison.CONTAINS_ONLY, content);
    }
}
