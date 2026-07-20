package com.decathlon.tzatziki.utils;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.ZoneId;
import java.time.chrono.MinguoDate;

class TimeTest {

    @ParameterizedTest
    @CsvSource({
            "the first Sunday of November 2020 at midnight,                                             2020-11-01T00:00:00Z",
            "first Sunday of November 2020 at midnight as an instant,                                   2020-11-01T00:00:00Z",
            "first Sunday of November 2020 at midnight in milliseconds,                                 1604188800000",
            "first Sunday of November 2020 at midnight as a long,                                       1604188800000",
            "first Sunday of November 2020 at midnight as a timestamp,                                  1604188800000",
            "first Sunday of November 2020 at midnight in seconds,                                      1604188800",
            "first Sunday of November 2020 at midnight (Europe/Paris) as a timestamp,                   1604185200000",
            "first Sunday of November 2020 at midnight (Europe/Paris) as a localdate,                   2020-11-01",
            "first Sunday of November 2020 at midnight (Europe/Paris) as a localdatetime,               2020-11-01T00:00:00",
            "first Sunday of November 2020 at midnight (Europe/Paris) as a zoneddatetime,               2020-11-01T00:00:00+01:00",
            "first Sunday of November 2020 at midnight (Europe/Paris) as an offsetdatetime,             2020-11-01T00:00:00+01:00",
            "first Sunday of November 2020 as a formatted date YYYY-MM-dd,                              2020-11-01",
            "first Sunday of November 2020 at midnight as a formatted date YYYY-MM-dd'T'HH:mm:ss.SSS,   2020-11-01T00:00:00.000",
    })
    @SuppressWarnings("java:S2699")
    void parse(String expression, String result) {
        Asserts.equals(Time.parse(expression), result);
    }

    @Test
    void addCustomTypeAdapter() {
        Time.addCustomTypeAdapter("minguodate", (date, zoneId) -> MinguoDate.from(date.toInstant().atZone(zoneId).toLocalDate()));

        Assertions.assertThat((MinguoDate) Time.parse("the first Sunday of November 2020 at midnight as a minguodate"))
                .isEqualTo(MinguoDate.from(Instant.ofEpochMilli(1604188800000L).atZone(ZoneId.of("UTC")).toLocalDate()));
    }
}
