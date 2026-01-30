package com.decathlon.tzatziki.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.decathlon.tzatziki")
@EnableJpaRepositories(basePackages = {"com.another_org", "com.decathlon.tzatziki.app.dao"})
@EnableJdbcRepositories(basePackages = {"com.decathlon.tzatziki.app.jdbc"})
@EntityScan(basePackages = {"com.another_org", "com.decathlon.tzatziki.app"})
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
