package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.Asserts;
import com.google.common.base.Splitter;
import io.cucumber.java.Before;

public class AddCustomFlagSteps {
    static {
        Asserts.addFlag("isEvenAndInBounds", (input, expected) -> {
            String[] bounds = Splitter.on('|').trimResults().omitEmptyStrings().splitToList(expected).toArray(String[]::new);
            int inputInt = Integer.parseInt(input);
            int min = Integer.parseInt(bounds[0]);
            int max = Integer.parseInt(bounds[1]);
            org.junit.jupiter.api.Assertions.assertTrue(() -> inputInt >= min && inputInt <= max && inputInt % 2 == 0);
        });
    }

    @Before
    public void instantiate(){}
}