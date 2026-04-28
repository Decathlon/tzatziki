package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.DatabaseCleaner;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import lombok.SneakyThrows;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

public class DatabaseTestSteps {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    private static DataSource dataSource;

    static {
        postgres.start();
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(postgres.getJdbcUrl());
        ds.setUser(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        dataSource = ds;
        DatabaseSteps.registerDataSource(dataSource);

        // Create test table
        createTestTable();
    }

    @SneakyThrows
    private static void createTestTable() {
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS test_table (id SERIAL PRIMARY KEY, name VARCHAR(255))");
        }
    }

    @SneakyThrows
    @Given("the test table has data")
    public void the_test_table_has_data() {
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            stmt.execute("INSERT INTO test_table (name) VALUES ('test1'), ('test2')");
        }
    }

    @SneakyThrows
    @Then("after cleaning the test table is empty")
    public void after_cleaning_the_test_table_is_empty() {
        DatabaseCleaner.clean(dataSource, "public");
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT count(*) FROM test_table")) {
            rs.next();
            assertThat(rs.getInt(1)).isZero();
        }
    }

    @SneakyThrows
    @Then("triggers are disabled on the test table")
    public void triggers_are_disabled_on_the_test_table() {
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT tgenabled FROM pg_trigger WHERE tgrelid = 'test_table'::regclass")) {
            while (rs.next()) {
                assertThat(rs.getString("tgenabled")).isEqualTo("D");
            }
        }
    }

    @SneakyThrows
    @Then("triggers are enabled on the test table")
    public void triggers_are_enabled_on_the_test_table() {
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT tgenabled FROM pg_trigger WHERE tgrelid = 'test_table'::regclass")) {
            while (rs.next()) {
                assertThat(rs.getString("tgenabled")).isEqualTo("O");
            }
        }
    }
}
