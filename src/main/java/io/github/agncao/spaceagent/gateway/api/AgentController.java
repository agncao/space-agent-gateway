package io.github.agncao.spaceagent.gateway.api;

import io.github.agncao.spaceagent.gateway.application.PersistentGatewayService;
import io.github.agncao.spaceagent.gateway.domain.agent.AgentManifest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {
    private final PersistentGatewayService service;

    public AgentController(PersistentGatewayService service) {
        this.service = service;
    }

    @PostMapping("/registrations")
    public ResponseEntity<Void> register(@RequestBody AgentManifest manifest) {
        service.register(manifest);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/catalog")
    public List<AgentManifest> catalog() {
        return service.catalog();
    }
}
