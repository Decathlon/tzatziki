package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.PlainJpaBackend;
import io.cucumber.java.Before;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Test configuration for tzatziki-jpa module.
 * Creates a pure JPA environment (no Spring) using Testcontainers PostgreSQL.
 */
public class JpaTestSteps {

    private static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16").withTmpFs(Map.of("/var/lib/postgresql/data", "rw")).waitingFor(Wait.forListeningPort());

    private static final DataSource dataSource;
    private static final EntityManagerFactory entityManagerFactory;

    static {
        postgres.start();

        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(postgres.getJdbcUrl());
        ds.setUser(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        dataSource = ds;

        // Create EntityManagerFactory using Hibernate as JPA provider
        Map<String, Object> props = new HashMap<>();
        props.put("jakarta.persistence.jdbc.url", postgres.getJdbcUrl());
        props.put("jakarta.persistence.jdbc.user", postgres.getUsername());
        props.put("jakarta.persistence.jdbc.password", postgres.getPassword());
        props.put("jakarta.persistence.jdbc.driver", "org.postgresql.Driver");
        props.put("hibernate.hbm2ddl.auto", "create-drop");
        props.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        props.put("hibernate.show_sql", "false");

        entityManagerFactory = Persistence.createEntityManagerFactory("test-pu", props);
    }

    @Before(order = 1)
    public void registerBackend() {
        JpaSteps.registerBackend(new PlainJpaBackend(entityManagerFactory, dataSource));
    }

}
