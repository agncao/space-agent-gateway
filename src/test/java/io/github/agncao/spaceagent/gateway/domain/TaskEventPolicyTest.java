package io.github.agncao.spaceagent.gateway.domain;

import io.github.agncao.spaceagent.gateway.domain.policy.GatewayPolicyException;
import io.github.agncao.spaceagent.gateway.domain.policy.TaskEventPolicy;
import io.github.agncao.spaceagent.gateway.domain.task.TaskEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskEventPolicyTest {
    @Test
    void rejectsEventSequenceGap() {
        TaskEventPolicy policy = new TaskEventPolicy();
        policy.validateSequence(0, new TaskEvent("evt-1", 1, "exec-1", "analysis-agent", List.of()));

        assertThatThrownBy(() ->
                policy.validateSequence(1, new TaskEvent("evt-3", 3, "exec-1", "analysis-agent", List.of())))
                .isInstanceOf(GatewayPolicyException.class)
                .hasMessageContaining("EVENT_OUT_OF_ORDER");
    }

    @Test
    void rejectsEffectNotDeclaredByManifest() {
        TaskEventPolicy policy = new TaskEventPolicy();
        TaskEvent event = new TaskEvent(
                "evt-1", 1, "exec-1", "analysis-agent", List.of("scene.opened"));

        assertThatThrownBy(() -> policy.validateEffects(event, List.of("analysis.illumination.completed")))
                .isInstanceOf(GatewayPolicyException.class)
                .hasMessageContaining("UNAUTHORIZED_EFFECT");
    }

    @Test
    void rejectsActionNotDeclaredByManifest() {
        TaskEventPolicy policy = new TaskEventPolicy();

        assertThatThrownBy(() -> policy.validateAction("scene.delete", List.of("analysis.present_result")))
                .isInstanceOf(GatewayPolicyException.class)
                .hasMessageContaining("UNAUTHORIZED_ACTION");
    }

    @Test
    void rejectsInvalidationNotDeclaredByManifest() {
        TaskEventPolicy policy = new TaskEventPolicy();
        TaskEvent event = new TaskEvent(
                "v1", "evt-1", 1, "exec-1", "scene-agent", "task.succeeded",
                java.time.Instant.now(),
                new io.github.agncao.spaceagent.gateway.domain.task.StepResult(
                        "success", "OK", "done", java.util.Map.of(), List.of(), List.of("entity.focused"),
                        List.of(), List.of()),
                java.util.Map.of());

        assertThatThrownBy(() -> policy.validateInvalidates(event, List.of("scene.none")))
                .isInstanceOf(GatewayPolicyException.class)
                .hasMessageContaining("UNAUTHORIZED_EFFECT");
    }
}
