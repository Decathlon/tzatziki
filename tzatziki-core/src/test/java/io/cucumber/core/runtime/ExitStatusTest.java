package io.cucumber.core.runtime;

import io.cucumber.core.eventbus.EventBus;
import io.cucumber.core.options.RuntimeOptions;
import io.cucumber.messages.types.Duration;
import io.cucumber.messages.types.Envelope;
import io.cucumber.messages.types.TestRunHookFinished;
import io.cucumber.messages.types.TestStepResult;
import io.cucumber.messages.types.TestStepResultStatus;
import io.cucumber.messages.types.Timestamp;
import io.cucumber.plugin.event.Status;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExitStatusTest {

    @Test
    void failedGlobalHooksFailTheRun() {
        EventBus bus = new TimeServiceEventBus(Clock.systemUTC(), UUID::randomUUID);
        ExitStatus exitStatus = new ExitStatus(RuntimeOptions.defaultOptions());
        exitStatus.setEventPublisher(bus);

        bus.send(hookFinished("passed", TestStepResultStatus.PASSED));
        bus.send(hookFinished("failed", TestStepResultStatus.FAILED));

        assertThat(exitStatus.isSuccess()).isFalse();
        assertThat(exitStatus.getStatus()).isEqualTo(Status.FAILED);
        assertThat(exitStatus.exitStatus()).isEqualTo((byte) 1);
    }

    private Envelope hookFinished(String id, TestStepResultStatus status) {
        TestStepResult result = new TestStepResult(new Duration(0L, 0), null, status, null);
        return Envelope.of(new TestRunHookFinished(id, result, new Timestamp(0L, 0)));
    }
}
