package io.github.agncao.spaceagent.gateway.domain.agent;

import java.util.List;

public record AgentManifest(
        String worker,
        String serviceName,
        String agentVersion,
        String protocolVersion,
        String description,
        List<String> requires,
        List<String> provides,
        List<String> invalidates,
        List<String> allowedActions) {
}
