package com.decathlon.tzatziki.utils;

import com.decathlon.tzatziki.app.model.Evilness;
import com.decathlon.tzatziki.steps.DatabaseSteps;
import com.decathlon.tzatziki.steps.JpaSteps;
import com.decathlon.tzatziki.steps.SpringJPASteps;
import com.decathlon.tzatziki.steps.SpringSteps;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringJpaBackendTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    private static final StaticApplicationContext context = new StaticApplicationContext();
    private static final LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
    private static PGSimpleDataSource dataSource;
    private SpringJpaBackend backend;

    @BeforeAll
    static void startDatabase() {
        postgres.start();
        dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        factory.setDataSource(dataSource);
        factory.setManagedTypes(PersistenceManagedTypes.of(Evilness.class.getName()));
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
        factory.afterPropertiesSet();
    }

    @AfterAll
    static void stopDatabase() {
        factory.destroy();
        context.close();
        postgres.stop();
    }

    @BeforeEach
    void initializeBackend() {
        resetConfiguration();
        new JdbcBackend(dataSource).truncate("evilness");
        backend = new SpringJpaBackend(context, List.of(factory));
    }

    @AfterEach
    void resetConfiguration() {
        DatabaseSteps.clearDataSources();
        DatabaseSteps.autoclean = true;
        DatabaseSteps.schemasToClean = List.of("public");
        SpringJPASteps.autoclean = true;
        SpringJPASteps.schemasToClean = List.of("public");
    }

    @Test
    void preservesConfigurationSetThroughDatabaseSteps() {
        DatabaseSteps.autoclean = false;
        DatabaseSteps.schemasToClean = List.of("custom_schema");

        springSteps().before();

        assertThat(DatabaseSteps.autoclean).isFalse();
        assertThat(DatabaseSteps.schemasToClean).containsExactly("custom_schema");
        assertThat(SpringJPASteps.autoclean).isFalse();
        assertThat(SpringJPASteps.schemasToClean).containsExactly("custom_schema");
    }

    @Test
    void legacyConfigurationUpdatesTheSameFieldsWithoutAHook() {
        SpringJPASteps.autoclean = false;
        SpringJPASteps.schemasToClean = List.of("legacy_schema");

        assertThat(DatabaseSteps.autoclean).isFalse();
        assertThat(DatabaseSteps.schemasToClean).containsExactly("legacy_schema");
    }

    @Test
    void readsThroughTheCurrentSpringTransaction() {
        transaction().executeWithoutResult(status -> {
            EntityManager current = EntityManagerFactoryUtils.getTransactionalEntityManager(factory.getObject());
            Evilness entity = new Evilness();
            current.persist(entity);
            current.flush();

            assertThat(backend.count(Evilness.class)).isEqualTo(1);
            assertThat(backend.findAll(Evilness.class)).containsExactly(entity);
            assertThat(current.isOpen()).isTrue();
            status.setRollbackOnly();
        });

        assertThat(backend.count(Evilness.class)).isZero();
    }

    @Test
    void readsWithoutAnExistingTransaction() {
        transaction().executeWithoutResult(status ->
                EntityManagerFactoryUtils.getTransactionalEntityManager(factory.getObject()).persist(new Evilness()));

        assertThat(backend.count(Evilness.class)).isEqualTo(1);
        assertThat(backend.findAll(Evilness.class)).hasSize(1);
    }

    @Test
    void savesRequireARepository() {
        List<Evilness> entities = List.of(new Evilness());
        assertThatThrownBy(() -> backend.saveAll(Evilness.class, entities))
                .isInstanceOf(AssertionError.class)
                .hasMessage(Evilness.class + " is not an Entity!");
    }

    @Test
    void replacingTheBackendReplacesItsDataSources() {
        try (HikariDataSource oldPool = pool(); HikariDataSource newPool = pool()) {
            JpaSteps.registerBackend(new JpaBackend(factory.getObject(), oldPool));
            oldPool.close();

            JpaSteps.registerBackend(new JpaBackend(factory.getObject(), newPool));
            DatabaseSteps.registerDataSource(dataSource);

            assertThat(DatabaseSteps.getRegisteredDataSources()).containsExactly(newPool, dataSource);
            new DatabaseSteps(null).before();
        }
    }

    @Test
    void repeatedRegistrationsDoNotDuplicateDataSources() {
        DatabaseSteps.registerDataSource(dataSource);
        JpaSteps.registerBackend(backend);
        JpaSteps.registerBackend(backend);

        assertThat(DatabaseSteps.getRegisteredDataSources()).containsExactly(dataSource);
        assertThat(DatabaseSteps.getBackend()).isSameAs(backend);
    }

    private SpringJPASteps springSteps() {
        SpringSteps spring = new SpringSteps(null, null) {
            @Override
            public ApplicationContext applicationContext() {
                return context;
            }
        };
        return new SpringJPASteps(null, spring, null, null, List.of(factory));
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(new JpaTransactionManager(factory.getObject()));
    }

    private HikariDataSource pool() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setMaximumPoolSize(1);
        return new HikariDataSource(config);
    }
}
