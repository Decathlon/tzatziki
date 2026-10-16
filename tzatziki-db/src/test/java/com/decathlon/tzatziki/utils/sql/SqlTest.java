package com.decathlon.tzatziki.utils.sql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlTest {

    @Test
    void rendersStatements() {
        assertThat(Sql.insert("inventory.stocks", List.of("id", "quantity")))
                .isEqualTo("INSERT INTO inventory.stocks (id, quantity) VALUES (?, ?)");
        assertThat(Sql.insert("stocks", List.of())).isEqualTo("INSERT INTO stocks DEFAULT VALUES");
        assertThat(Sql.select("stocks")).isEqualTo("SELECT * FROM stocks");
        assertThat(Sql.count("stocks")).isEqualTo("SELECT COUNT(*) FROM stocks");
        assertThat(Sql.truncate("stocks")).isEqualTo("TRUNCATE stocks RESTART IDENTITY CASCADE");
        assertThat(Sql.truncate(SqlIdentifier.quoted("public", "Mixed\"Case")))
                .isEqualTo("TRUNCATE \"public\".\"Mixed\"\"Case\" RESTART IDENTITY CASCADE");
    }

    @Test
    void acceptsValidIdentifiers() {
        assertThat(SqlIdentifier.schema("_schema1").value()).isEqualTo("_schema1");
        assertThat(SqlIdentifier.table("schema.table_2").value()).isEqualTo("schema.table_2");
        assertThat(SqlIdentifier.columns(List.of("a", "B"))).extracting(SqlIdentifier::value).containsExactly("a", "B");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsMissingIdentifiers(String identifier) {
        assertThatThrownBy(() -> SqlIdentifier.table(identifier))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("table name must not be null or empty");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1table", "users; DROP TABLE users", "a.", "a..b", "\"quoted\"", "with space", "a-b"})
    void rejectsInvalidIdentifiers(String identifier) {
        assertThatThrownBy(() -> SqlIdentifier.column(identifier))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid column name: '" + identifier + "'");
        assertThatThrownBy(() -> Sql.insert("users", List.of(identifier))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullValuesAndParts() {
        assertThatThrownBy(() -> new SqlIdentifier(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SqlIdentifier.quoted("schema", null)).isInstanceOf(NullPointerException.class);
    }
}
