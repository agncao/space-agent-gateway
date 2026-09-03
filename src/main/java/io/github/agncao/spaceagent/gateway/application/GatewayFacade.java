package io.github.agncao.spaceagent.gateway.application;

import io.github.agncao.spaceagent.gateway.domain.agent.AgentManifest;
import io.github.agncao.spaceagent.gateway.domain.policy.GatewayPolicyException;
import io.github.agncao.spaceagent.gateway.domain.task.TaskAccepted;
import io.github.agncao.spaceagent.gateway.domain.task.TaskLedger;
import io.github.agncao.spaceagent.gateway.domain.task.TaskSubmission;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GatewayFacade {
    private final TaskLedger taskLedger;
    private final Map<String, AgentManifest> manifests = new LinkedHashMap<>();

    public GatewayFacade(TaskLedger taskLedger) {
        this.taskLedger = taskLedger;
    }

    public void register(AgentManifest manifest) {
        manifests.put(manifest.worker(), manifest);
    }

    public TaskAccepted submit(TaskSubmission submission) {
        if (!manifests.containsKey(submission.worker())) {
            throw new GatewayPolicyException("WORKER_NOT_REGISTERED: " + submission.worker());
        }
        return taskLedger.accept(submission);
    }

    public List<AgentManifest> catalog() {
        return List.copyOf(manifests.values());
    }
}
