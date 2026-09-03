package io.github.agncao.spaceagent.gateway.domain.task;

import java.util.List;
import java.util.Map;

public record StepResult(
        String status,
        String code,
        String summary,
        Map<String, Object> data,
        List<String> effects,
        List<String> invalidates,
        List<String> requirements,
        List<Map<String, Object>> artifacts) {
    public StepResult {
        data = data == null ? Map.of() : Map.copyOf(data);
        effects = effects == null ? List.of() : List.copyOf(effects);
        invalidates = invalidates == null ? List.of() : List.copyOf(invalidates);
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }
}
