package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.DatabaseCleaner;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import lombok.SneakyThrows;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

public class DatabaseTestSteps {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine").waitingFor(Wait.forListeningPort());
    private static DataSource dataSource;

    static {
        postgres.start();
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(postgres.getJdbcUrl());
        ds.setUser(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        dataSource = ds;
        DatabaseSteps.registerDataSource(dataSource);

        createSchema();
    }

    @SneakyThrows
    private static void createSchema() {
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            // Basic test table for cleaner tests
            stmt.execute("CREATE TABLE IF NOT EXISTS test_table (id SERIAL PRIMARY KEY, name VARCHAR(255))");

            // Products table for table-level step tests
            stmt.execute("CREATE TABLE IF NOT EXISTS products (id INTEGER PRIMARY KEY, name VARCHAR(255), price NUMERIC(10,2))");

            // Audit log table for trigger tests
            stmt.execute("CREATE TABLE IF NOT EXISTS audit_log (id SERIAL PRIMARY KEY, table_name VARCHAR(255), operation VARCHAR(50))");

            // Triggered table with an INSERT trigger that writes to audit_log
            stmt.execute("CREATE TABLE IF NOT EXISTS triggered_table (id INTEGER PRIMARY KEY, name VARCHAR(255))");
            stmt.execute("""
                CREATE OR REPLACE FUNCTION log_insert() RETURNS TRIGGER AS $$
                BEGIN
                    INSERT INTO audit_log (table_name, operation) VALUES (TG_TABLE_NAME, TG_OP);
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql;
            """);
            stmt.execute("""
                CREATE OR REPLACE TRIGGER triggered_table_insert_trigger
                AFTER INSERT ON triggered_table
                FOR EACH ROW EXECUTE FUNCTION log_insert();
            """);
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
