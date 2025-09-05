package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.app.TestApplication;
import com.decathlon.tzatziki.utils.Guard;
import com.decathlon.tzatziki.utils.Patterns;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.spring.CucumberContextConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ContextConfiguration;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = RANDOM_PORT, classes = TestApplication.class)
@ContextConfiguration(initializers = TestApplicationSteps.Initializer.class)
@Slf4j
public class TestApplicationSteps {
    private static Future<?> completableFutureToTest;

    private final ObjectSteps objectSteps;

    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    public TestApplicationSteps(ObjectSteps objectSteps) {
        this.objectSteps = objectSteps;
    }


    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
        }
    }

    @Given(Patterns.THAT + "the thread pool executor is (not )?cleaned between test runs")
    public void thread_pool_executor_clean(String negation) {
        SpringSteps.clearThreadPoolExecutor = !"not ".equals(negation);
    }

    @Given(Patterns.THAT + "we start an infinite task")
    public void start_infinite_task() {
        completableFutureToTest = taskExecutor.submit(() -> {
            try {
                new CountDownLatch(1).await(1000, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Then(Patterns.THAT + GUARD + "infinite task has been shutdown")
    public void infinite_task_has_been_shutdown(Guard guard) {
        guard.in(objectSteps, () -> Assertions.assertTrue(completableFutureToTest.isDone()));
    }

}
