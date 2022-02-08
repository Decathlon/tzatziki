package com.decathlon.tzatziki.steps;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.flyway.FlywayDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.decathlon.tzatziki.app.dao.user", entityManagerFactoryRef = "userEntityManagerFactory", transactionManagerRef = "userTransactionManager")
public class PersistenceUserAutoConfiguration {

    @Bean(name = "userDataSource")
    @ConfigurationProperties(prefix = "spring.datasource")
    @Primary
    @FlywayDataSource
    public DataSource dataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "userEntityManagerFactory")
    @Primary
    public LocalContainerEntityManagerFactoryBean
    userEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("userDataSource") DataSource dataSource
    ) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("db.migration/usersDB")
                .load()
                .migrate();
        return
                builder
                        .dataSource(dataSource)
                        .packages("com.decathlon.tzatziki.app.model.user")
                        .persistenceUnit("user")
                        .build();
    }

    @Bean(name = "userTransactionManager")
    @Primary
    public PlatformTransactionManager userTransactionManager(
            @Qualifier("userEntityManagerFactory") EntityManagerFactory
                    userEntityManagerFactory
    ) {
        return new JpaTransactionManager(userEntityManagerFactory);
    }

}
