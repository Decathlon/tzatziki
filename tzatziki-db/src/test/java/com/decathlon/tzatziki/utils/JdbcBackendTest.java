package com.decathlon.tzatziki.utils;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class JdbcBackendTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    private static PGSimpleDataSource dataSource;

    @BeforeAll
    static void startDatabase() {
        postgres.start();
        dataSource = dataSource(true);
    }

    @AfterAll
    static void stopDatabase() {
        postgres.stop();
    }

    @BeforeEach
    void createTable() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS fixtures");
            statement.execute("CREATE TABLE fixtures (id SERIAL PRIMARY KEY, name TEXT NOT NULL DEFAULT 'anonymous', enabled BOOLEAN DEFAULT true)");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void commitsInsertsAndTruncatesRegardlessOfAutoCommit(boolean autoCommit) {
        JdbcBackend backend = new JdbcBackend(dataSource(autoCommit));
        JdbcBackend reader = new JdbcBackend(dataSource);

        backend.insertRows("fixtures", List.of(Map.of("name", "first")));
        assertThat(reader.count("fixtures")).isEqualTo(1);

        backend.truncate("fixtures");
        assertThat(reader.count("fixtures")).isZero();

        backend.insertRows("fixtures", List.of(Map.of("name", "replacement")));
        assertThat(reader.queryAll("fixtures", null)).singleElement().satisfies(row ->
                assertThat(row).containsEntry("id", 1).containsEntry("name", "replacement"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rollsBackEarlierRowsWhenAnInsertFails(boolean autoCommit) {
        JdbcBackend backend = new JdbcBackend(dataSource(autoCommit));
        Map<String, Object> invalidRow = new HashMap<>();
        invalidRow.put("name", null);
        invalidRow.put("enabled", false);

        assertThatThrownBy(() -> backend.insertRows("fixtures", List.of(Map.of("name", "valid"), invalidRow)))
                .isInstanceOf(SQLException.class);
        assertThat(new JdbcBackend(dataSource).count("fixtures")).isZero();
    }

    @Test
    void preservesDefaultsExplicitNullsAndRowOrder() {
        Map<String, Object> explicitNull = new HashMap<>();
        explicitNull.put("name", "fourth");
        explicitNull.put("enabled", null);
        JdbcBackend backend = new JdbcBackend(dataSource);

        backend.insertRows("fixtures", List.of(
                Map.of("name", "first"),
                Map.of("name", "second", "enabled", false),
                Map.of("name", "third"),
                explicitNull));

        assertThat(backend.queryAll("fixtures", null))
                .extracting(row -> row.get("id"), row -> row.get("name"), row -> row.get("enabled"))
                .containsExactlyInAnyOrder(
                        tuple(1, "first", true),
                        tuple(2, "second", false),
                        tuple(3, "third", true),
                        tuple(4, "fourth", null));
    }

    @Test
    void insertsDefaultValuesForEmptyRows() {
        JdbcBackend backend = new JdbcBackend(dataSource);

        backend.insertRows("fixtures", List.of(Map.of()));

        assertThat(backend.queryAll("fixtures", null))
                .extracting(row -> row.get("id"), row -> row.get("name"), row -> row.get("enabled"))
                .containsExactly(tuple(1, "anonymous", true));
    }

    @Test
    void allowsGeneratedAndExplicitIdsInTheSameInsertion() {
        JdbcBackend backend = new JdbcBackend(dataSource);

        backend.insertRows("fixtures", List.of(Map.of("name", "generated"), Map.of("id", 100, "name", "explicit")));

        assertThat(backend.queryAll("fixtures", null))
                .extracting(row -> row.get("id"), row -> row.get("name"))
                .containsExactlyInAnyOrder(tuple(1, "generated"), tuple(100, "explicit"));
    }

    @Test
    void convertsStringValuesToTheColumnTypes() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS typed");
            statement.execute("""
                    CREATE TABLE typed (id SERIAL PRIMARY KEY, i INTEGER, s SMALLINT, b BIGINT, r REAL, d DOUBLE PRECISION,
                    n NUMERIC(10,2), flag BOOLEAN, day DATE, at_time TIME, ts TIMESTAMP, tstz TIMESTAMPTZ, txt TEXT)""");
        }
        JdbcBackend backend = new JdbcBackend(dataSource);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("i", "1");
        row.put("s", "2");
        row.put("b", "3000000000");
        row.put("r", "1.5");
        row.put("d", "2.25");
        row.put("n", "3.10");
        row.put("flag", "true");
        row.put("day", "2020-01-02");
        row.put("at_time", "10:11:12");
        row.put("ts", "2020-01-02T03:04:05");
        row.put("tstz", "2020-01-02T03:04:05Z");
        row.put("txt", "text");
        Map<String, Object> otherTimestamps = new LinkedHashMap<>();
        otherTimestamps.put("ts", "2020-01-02 03:04:05");
        otherTimestamps.put("tstz", "2020-01-02T05:04:05+02:00");

        // mixed case: column types are resolved through the lower-case table name, like PostgreSQL does
        backend.insertRows("Typed", List.of(row, otherTimestamps));

        List<Map<String, Object>> rows = backend.queryAll("typed", null);
        Instant localTimestamp = Timestamp.valueOf(LocalDateTime.parse("2020-01-02T03:04:05")).toInstant();
        assertThat(rows.get(0))
                .containsEntry("i", 1)
                .containsEntry("s", 2)
                .containsEntry("b", 3000000000L)
                .containsEntry("r", 1.5f)
                .containsEntry("d", 2.25d)
                .containsEntry("n", new BigDecimal("3.10"))
                .containsEntry("flag", true)
                .containsEntry("day", LocalDate.parse("2020-01-02"))
                .containsEntry("at_time", LocalTime.parse("10:11:12"))
                .containsEntry("ts", localTimestamp)
                .containsEntry("tstz", Instant.parse("2020-01-02T03:04:05Z"))
                .containsEntry("txt", "text");
        assertThat(rows.get(1))
                .containsEntry("ts", localTimestamp)
                .containsEntry("tstz", Instant.parse("2020-01-02T03:04:05Z"));
    }

    private static PGSimpleDataSource dataSource(boolean autoCommit) {
        PGSimpleDataSource source = new PGSimpleDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                Connection connection = super.getConnection();
                connection.setAutoCommit(autoCommit);
                return connection;
            }
        };
        source.setUrl(postgres.getJdbcUrl());
        source.setUser(postgres.getUsername());
        source.setPassword(postgres.getPassword());
        return source;
    }
}
