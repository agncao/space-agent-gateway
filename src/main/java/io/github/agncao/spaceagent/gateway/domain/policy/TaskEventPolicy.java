package io.github.agncao.spaceagent.gateway.domain.policy;

import io.github.agncao.spaceagent.gateway.domain.task.TaskEvent;

import java.util.List;

public final class TaskEventPolicy {
    public void validateSequence(long lastSequence, TaskEvent event) {
        long expected = lastSequence + 1;
        if (event.eventSeq() != expected) {
            throw new GatewayPolicyException(
                    "EVENT_OUT_OF_ORDER: expected " + expected + " but received " + event.eventSeq());
        }
    }

    public void validateEffects(TaskEvent event, List<String> allowedEffects) {
        for (String effect : event.effects()) {
            if (!allowedEffects.contains(effect)) {
                throw new GatewayPolicyException("UNAUTHORIZED_EFFECT: " + effect);
            }
        }
    }

    public void validateInvalidates(TaskEvent event, List<String> allowedInvalidates) {
        for (String invalidated : event.invalidates()) {
            if (!allowedInvalidates.contains(invalidated)) {
                throw new GatewayPolicyException("UNAUTHORIZED_EFFECT: invalidates " + invalidated);
            }
        }
    }

    public void validateAction(String action, List<String> allowedActions) {
        if (!allowedActions.contains(action)) {
            throw new GatewayPolicyException("UNAUTHORIZED_ACTION: " + action);
        }
    }
}
