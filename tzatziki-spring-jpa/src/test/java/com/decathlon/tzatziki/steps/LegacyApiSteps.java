package com.decathlon.tzatziki.steps;

import org.springframework.data.domain.Sort;
import com.decathlon.tzatziki.app.dao.UserDataSpringRepository;
import com.decathlon.tzatziki.app.model.Group;
import com.decathlon.tzatziki.app.model.User;
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

    @Then("^the legacy API table and entity delegates work$")
    public void legacy_delegates() {
        Guard always = Guard.always();
        springJPASteps.the_table_will_contain(always, "users", InsertionMode.ONLY, "- firstName: Darth\n  lastName: Vader");
        springJPASteps.the_table_contains(always, "users", Comparison.CONTAINS_ONLY, "- firstName: Darth");
        springJPASteps.the_entities_will_contain(always, User.class, InsertionMode.AT_LEAST, "- firstName: Han\n  lastName: Solo");
        springJPASteps.the_entities_contain(always, User.class, Comparison.CONTAINS, "- firstName: Han");
        springJPASteps.add_table_content_to_variable(always, "legacyTable", "users");
        springJPASteps.add_entities_to_variable(always, "legacyEntities", User.class);
        springJPASteps.add_repository_content_to_variable(always, "legacyRepository", springJPASteps.getRepositoryForEntity(User.class));
        springJPASteps.add_ordered_repository_content_to_variable(always, "legacyOrdered",
                springJPASteps.getRepositoryByType(UserDataSpringRepository.class), Sort.by(Sort.Order.desc("firstName")));
        springJPASteps.enable_triggers(always, "enabled");
        springJPASteps.the_repository_will_contain_with_type(always, springJPASteps.getRepositoryForTable("users"), InsertionMode.ONLY, User.class, "- firstName: Luke\n  lastName: Skywalker");
        springJPASteps.the_table_contains(always, "users", Comparison.CONTAINS_ONLY, "- firstName: Luke");
        springJPASteps.the_table_will_contain(always, "groups", InsertionMode.ONLY, "[]");
        springJPASteps.the_table_contains_nothing(always, "groups");
        springJPASteps.the_entities_contain_nothing(always, Group.class);
    }
}
