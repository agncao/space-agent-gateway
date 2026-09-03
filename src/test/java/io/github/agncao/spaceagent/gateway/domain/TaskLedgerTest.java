package io.github.agncao.spaceagent.gateway.domain;

import io.github.agncao.spaceagent.gateway.domain.task.TaskAccepted;
import io.github.agncao.spaceagent.gateway.domain.task.TaskLedger;
import io.github.agncao.spaceagent.gateway.domain.task.TaskSubmission;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TaskLedgerTest {
    @Test
    void duplicateIdempotencyKeyReturnsOriginalExecution() {
        TaskLedger ledger = new TaskLedger(() -> "exec-1");
        TaskSubmission submission = new TaskSubmission(
                "v1", "orchestrator-a", "run-1", "step-1", "analysis-agent",
                "分析光照数据", "分析它的光照数据", "run-1:step-1:1", Map.of());

        TaskAccepted first = ledger.accept(submission);
        TaskAccepted duplicate = ledger.accept(submission);

        assertThat(first.executionId()).isEqualTo("exec-1");
        assertThat(duplicate.executionId()).isEqualTo("exec-1");
        assertThat(duplicate.duplicate()).isTrue();
        assertThat(ledger.size()).isEqualTo(1);
    }
}
