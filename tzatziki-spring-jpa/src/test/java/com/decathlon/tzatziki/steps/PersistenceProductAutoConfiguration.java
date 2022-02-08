package com.decathlon.tzatziki.steps;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.flyway.FlywayDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.decathlon.tzatziki.app.dao.product", entityManagerFactoryRef = "productEntityManagerFactory", transactionManagerRef = "productTransactionManager")
public class PersistenceProductAutoConfiguration {

    @Bean(name = "productDataSource")
    @ConfigurationProperties(prefix = "spring.second-datasource")
    @FlywayDataSource
    public DataSource dataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "productEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean
    productEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("productDataSource") DataSource dataSource
    ) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("db.migration/productsDB")
                .load()
                .migrate();
        return builder.dataSource(dataSource)
                .packages("com.decathlon.tzatziki.app.model.product")
                .persistenceUnit("product")
                .build();
    }

    @Bean(name = "productTransactionManager")
    public PlatformTransactionManager productTransactionManager(
            @Qualifier("productEntityManagerFactory") EntityManagerFactory
                    productEntityManagerFactory
    ) {
        return new JpaTransactionManager(productEntityManagerFactory);
    }

}
