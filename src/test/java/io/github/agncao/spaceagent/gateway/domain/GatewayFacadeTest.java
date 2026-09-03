package io.github.agncao.spaceagent.gateway.domain;

import io.github.agncao.spaceagent.gateway.application.GatewayFacade;
import io.github.agncao.spaceagent.contracts.v1.AgentManifest;
import io.github.agncao.spaceagent.contracts.v1.TaskAccepted;
import io.github.agncao.spaceagent.contracts.v1.TaskSubmission;
import io.github.agncao.spaceagent.gateway.domain.policy.GatewayPolicyException;
import io.github.agncao.spaceagent.gateway.domain.task.TaskLedger;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayFacadeTest {
    @Test
    void rejectsTaskForUnregisteredWorker() {
        GatewayFacade facade = new GatewayFacade(new TaskLedger(() -> "exec-1"));

        assertThatThrownBy(() -> facade.submit(submission()))
                .isInstanceOf(GatewayPolicyException.class)
                .hasMessageContaining("WORKER_NOT_REGISTERED");
    }

    @Test
    void registeredWorkerCanReceiveIdempotentTask() {
        GatewayFacade facade = new GatewayFacade(new TaskLedger(() -> "exec-1"));
        facade.register(new AgentManifest(
                "analysis-agent", "space-agent-analysis", "0.1.0", "v1",
                "分析", null, List.of("scene.opened"), List.of("analysis.illumination.completed"),
                List.of(), List.of("analysis.present_result")));

        TaskAccepted first = facade.submit(submission());
        TaskAccepted duplicate = facade.submit(submission());

        assertThat(first.executionId()).isEqualTo("exec-1");
        assertThat(duplicate.duplicate()).isTrue();
        assertThat(facade.catalog()).hasSize(1);
    }

    private static TaskSubmission submission() {
        return new TaskSubmission(
                "v1", "orchestrator-a", "run-1", "step-1", "analysis-agent",
                "分析光照数据", "分析它的光照数据", "user", Map.of(), List.of(), null,
                "run-1:step-1:1");
    }
}
