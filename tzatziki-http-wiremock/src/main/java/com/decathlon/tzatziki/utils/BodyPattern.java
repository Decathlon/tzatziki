package com.decathlon.tzatziki.utils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.tomakehurst.wiremock.matching.EagerMatchResult;
import com.github.tomakehurst.wiremock.matching.MatchResult;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;

import java.util.List;

public class BodyPattern extends StringValuePattern {

    private final Comparison comparison;

    public BodyPattern(@JsonProperty("compare") Comparison comparison, String expectedBody) {
        super(expectedBody);
        this.comparison = comparison;
    }


    //TO DO, correctly display the error with good format
    @Override
    public MatchResult match(String body) {
        try {
            comparison.compare(body.replace("\n", ""), expectedValue);
            return MatchResult.exactMatch();
        } catch (AssertionError e) {
            return new EagerMatchResult(1, List.of(), List.of(
                    new MatchResult.DiffDescription(
                            expectedValue,
                            body,
                            e.getMessage()
                    )
            ));
        }
    }
}
