package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.Asserts;
import com.google.common.base.Splitter;
import io.cucumber.java.Before;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class AddCustomFlagSteps {
    static {
        Asserts.addFlag("isEvenAndInBounds", (input, expected) -> {
            String[] bounds = Splitter.on('|').trimResults().omitEmptyStrings().splitToList(expected).toArray(String[]::new);
            int inputInt = Integer.parseInt(input);
            int min = Integer.parseInt(bounds[0]);
            int max = Integer.parseInt(bounds[1]);
            assertTrue(() -> inputInt >= min && inputInt <= max && inputInt % 2 == 0);
        });
    }

    @Before
    public void instantiate(){
        // just to trigger static block
    }
}