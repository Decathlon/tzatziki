package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.app.TestApplication;
import com.decathlon.tzatziki.spring.HttpInterceptor;
import com.decathlon.tzatziki.utils.Asserts;
import com.decathlon.tzatziki.utils.Patterns;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.spring.CucumberContextConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ContextConfiguration;

import java.util.concurrent.Future;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = RANDOM_PORT, classes = TestApplication.class)
@ContextConfiguration(initializers = TestApplicationSteps.Initializer.class)
@Slf4j
public class TestApplicationSteps {
    private static Future<?> completableFutureToTest;

    @Autowired
    ThreadPoolTaskExecutor taskExecutor;


    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
        }
    }

    @Before
    public void before() {
        HttpInterceptor.enable();
    }

    @Then("if we disable the HttpInterceptor")
    public void if_we_disable_the_http_interceptor() {
        HttpInterceptor.disable();
    }


    @Given(Patterns.THAT + "clean thread pool executor is (enabled|disabled)")
    public void clead_thread_pool_executor(String enabled) {
        SpringSteps.clearThreadPoolExecutor = "enabled".equals(enabled);

    }

    @Given(Patterns.THAT + "we start an infinite task")
    public void start_infinite_task() {
        completableFutureToTest = taskExecutor.submit(() -> {
            try {
                Thread.sleep(100000000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Then(Patterns.THAT + "infinite task (has been|has not been) shutdown")
    public void infinite_task_has_been_shutdown(String negation) {
        Asserts.equals(completableFutureToTest.isDone(), !"has not been".equals(negation));
    }

}
