package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.app.TestApplication;
import com.decathlon.tzatziki.kafka.CountService;
import com.decathlon.tzatziki.kafka.KafkaUsersListener;
import com.decathlon.tzatziki.kafka.KafkaUsersReplayer;
import com.decathlon.tzatziki.kafka.Seeker;
import com.decathlon.tzatziki.utils.Guard;
import io.cucumber.java.Before;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.CucumberContextConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;

import static com.decathlon.tzatziki.utils.Patterns.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = RANDOM_PORT, classes = TestApplication.class)
@ContextConfiguration(initializers = TestApplicationSteps.Initializer.class)
@Slf4j
public class TestApplicationSteps {

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            KafkaSteps.start();
            TestPropertyValues.of(
                    "spring.kafka.bootstrap-servers=" + KafkaSteps.bootstrapServers(),
                    "spring.kafka.consumer.properties.fetch.min.bytes=100000",
                    "spring.kafka.consumer.properties.fetch.max.wait.ms=1",
                    "spring.kafka.consumer.auto-offset-reset=earliest"
            ).applyTo(configurableApplicationContext.getEnvironment());
            KafkaSteps.autoSeekTopics("exposed-users", "json-users");
        }
    }

    ObjectSteps objects;

    public TestApplicationSteps(ObjectSteps objects) {
        this.objects = objects;
    }

    @SpyBean
    CountService spyCountService;

    @Autowired
    KafkaUsersListener listener;
    @Autowired
    KafkaUsersReplayer replayer;

    @Before
    public void before() {
        spyCountService.messageCountsPerTopic().clear();
    }

    @When(THAT + A_USER + "replay the topic " + VARIABLE + " from (the beginning|offset 0) with a (consumer|listener)$")
    public void we_replay(String topic, String from, String type) {
        Seeker seeker = type.equals("consumer") ? replayer : listener;
        if (from.equals("the beginning")) {
            seeker.seekToBeginning(topic);
        } else {
            seeker.seek(topic, 0);
        }
    }

    @Then(Guard.GUARD + "we resume replaying the topic " + VARIABLE + "$")
    public void we_resume_replaying_a_topic(Guard guard, String topic) {
        guard.in(objects, () -> replayer.resume(topic));
    }

    @Then(THAT + Guard.GUARD + "we have received (\\d+) messages? on the topic " + VARIABLE + "$")
    public void we_have_received_message_on_the_topic_users(Guard guard, Integer messageCount, String topic) {
        guard.in(objects, () ->
                assertThat(spyCountService.messageCountsPerTopic().getOrDefault(topic, -1)).isEqualTo(messageCount)
        );
    }

    @Then(THAT + "the message counter will success, error then success$")
    public void success_then_error_then_success_on_message_count() {
        Mockito.doCallRealMethod()
                .doAnswer(invocation -> {
                    throw new NullPointerException("expectedIssue");
                })
                .doAnswer(invocation -> {
                    Thread.sleep(1000);
                    return invocation.callRealMethod();
                })
                .when(spyCountService)
                .countMessage(Mockito.anyString());
    }
}
