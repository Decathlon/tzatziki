package com.decathlon.tzatziki.utils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.tomakehurst.wiremock.matching.MatchResult;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;

public class FlagPattern extends StringValuePattern {

    protected FlagPattern(@JsonProperty("match") String expectedValue) {
        super(expectedValue);
    }

    @Override
    public MatchResult match(String value) {
        try {
            Asserts.equals(value, super.expectedValue);
            return MatchResult.exactMatch();
        } catch (Error e) { // NOSONAR
            return MatchResult.noMatch();
        }
    }
}
