package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.Interaction;
import io.cucumber.java.Before;

public class LocalSteps {

    static {
        Interaction.printResponses = true;
    }

    @Before
    public void before() {}
}
