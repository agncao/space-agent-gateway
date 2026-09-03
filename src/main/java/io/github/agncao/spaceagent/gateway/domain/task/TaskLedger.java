package io.github.agncao.spaceagent.gateway.domain.task;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class TaskLedger {
    private final Supplier<String> executionIdSupplier;
    private final Map<String, TaskAccepted> tasks = new LinkedHashMap<>();

    public TaskLedger(Supplier<String> executionIdSupplier) {
        this.executionIdSupplier = executionIdSupplier;
    }

    public TaskAccepted accept(TaskSubmission submission) {
        TaskAccepted existing = tasks.get(submission.idempotencyKey());
        if (existing != null) {
            return new TaskAccepted(existing.executionId(), true);
        }
        TaskAccepted accepted = new TaskAccepted(executionIdSupplier.get(), false);
        tasks.put(submission.idempotencyKey(), accepted);
        return accepted;
    }

    public int size() {
        return tasks.size();
    }
}
