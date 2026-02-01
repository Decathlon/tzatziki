package com.decathlon.tzatziki.utils;

import com.joestelmach.natty.Parser;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Locale.ROOT;
import static java.util.Optional.ofNullable;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Time {

    private static final Map<String, BiFunction<Date, ZoneId, ?>> typeAdapters = new LinkedHashMap<>();

    // -> (Natty expression)( zoneId)?( type)?
    // example:
    public static final String TIME = "((?:(?! \\(| as | in ).)+)(?: \\(([\\w/]+)\\))?(?: (?:as an?|in) ([\\w .\\-':]+))?";
    public static final Pattern EXPRESSION = Pattern.compile("^" + TIME + "$");
    private static final int NATTY_EXPRESSION = 1;
    private static final int ZONE_ID = 2;
    private static final int TYPE = 3;

    private static Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    public static void setToNow() {
        set(Instant.now());
    }

    public static void set(Instant now) {
        Time.now = now.truncatedTo(ChronoUnit.MILLIS);
    }

    public static Instant now() {
        return now;
    }

    public static void addCustomTypeAdapter(String type, BiFunction<Date, ZoneId, ?> typeAdapter) {
        typeAdapters.put(type, typeAdapter);
    }

    /**
     *
     */
    @SuppressWarnings("unchecked")
    public static <T> T parse(String expression) {
        Matcher matcher = EXPRESSION.matcher(expression);
        if (matcher.matches()) {
            String nattyExpression = matcher.group(NATTY_EXPRESSION);
            ZoneId zoneId = ofNullable(matcher.group(ZONE_ID)).map(ZoneId::of).orElse(ZoneId.of("UTC"));
            String type = ofNullable(matcher.group(TYPE)).orElse("instant");

            Parser parser = new Parser(TimeZone.getTimeZone(zoneId));
            Date date = parser.parse(nattyExpression, new Date(now.toEpochMilli())).get(0).getDates().get(0);

            return (T) getTypeAdapter(type).apply(date, zoneId);
        }
        throw new IllegalArgumentException("expression: '%s' doesn't match pattern: '%s'".formatted(expression, TIME));
    }

    public static BiFunction<Date, ZoneId, ?> getTypeAdapter(String type) { // NOSONAR
        return typeAdapters.computeIfAbsent(type.toLowerCase(ROOT), t -> switch (t) {
            case "instant" -> (date, zoneId) -> date.toInstant();
            case "long", "timestamp", "milliseconds" -> (date, zoneId) -> date.getTime();
            case "seconds" -> (date, zoneId) -> date.toInstant().getEpochSecond();
            case "localdatetime" -> (date, zoneId) -> LocalDateTime.ofInstant(date.toInstant(), zoneId);
            case "zoneddatetime" -> (date, zoneId) -> ZonedDateTime.ofInstant(date.toInstant(), zoneId);
            case "offsetdatetime" -> (date, zoneId) -> OffsetDateTime.ofInstant(date.toInstant(), zoneId);
            case "localdate" -> (date, zoneId) -> date.toInstant().atZone(zoneId).toLocalDate();
            default -> (date, zoneId) -> {
                if (type.startsWith("formatted date")) {
                    return DateTimeFormatter.ofPattern(type.replaceAll("formatted date ", ""))
                            .format(ZonedDateTime.ofInstant(date.toInstant(), zoneId));
                }
                throw new AssertionError("'%s' cannot be evaluated as '%s'!".formatted(date, type));
            };
        });
    }

}
