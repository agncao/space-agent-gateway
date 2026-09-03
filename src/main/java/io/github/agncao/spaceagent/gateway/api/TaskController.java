package io.github.agncao.spaceagent.gateway.api;

import io.github.agncao.spaceagent.gateway.application.PersistentGatewayService;
import io.github.agncao.spaceagent.gateway.domain.task.TaskAccepted;
import io.github.agncao.spaceagent.gateway.domain.task.ActionRequest;
import io.github.agncao.spaceagent.gateway.domain.task.ActionResult;
import io.github.agncao.spaceagent.gateway.domain.task.TaskCommand;
import io.github.agncao.spaceagent.gateway.domain.task.TaskEvent;
import io.github.agncao.spaceagent.gateway.domain.task.TaskSubmission;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class TaskController {
    private final PersistentGatewayService service;

    public TaskController(PersistentGatewayService service) {
        this.service = service;
    }

    @PostMapping("/tasks")
    public ResponseEntity<TaskAccepted> submit(@RequestBody TaskSubmission submission) {
        return ResponseEntity.accepted().body(service.submit(submission));
    }

    @GetMapping("/tasks/{executionId}")
    public Map<String, Object> task(@PathVariable String executionId) {
        return service.task(executionId);
    }

    @PostMapping("/task-events")
    public ResponseEntity<Void> event(@RequestBody TaskEvent event) {
        service.acceptEvent(event);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/action-requests")
    public ResponseEntity<Void> actionRequest(@RequestBody ActionRequest request) {
        service.requestAction(request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/tasks/{executionId}/commands")
    public ResponseEntity<Void> command(@PathVariable String executionId, @RequestBody TaskCommand command) {
        if (!executionId.equals(command.executionId())) {
            throw new io.github.agncao.spaceagent.gateway.domain.policy.GatewayPolicyException(
                    "CONTRACT_VIOLATION: execution_id path/body mismatch");
        }
        service.command(command);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/tasks/{executionId}/actions/{actionId}/result")
    public ResponseEntity<Void> actionResult(
            @PathVariable String executionId,
            @PathVariable String actionId,
            @RequestBody ActionResult result) {
        if (!executionId.equals(result.executionId()) || !actionId.equals(result.actionId())) {
            throw new io.github.agncao.spaceagent.gateway.domain.policy.GatewayPolicyException(
                    "CONTRACT_VIOLATION: action path/body mismatch");
        }
        service.actionResult(result);
        return ResponseEntity.accepted().build();
    }
}
