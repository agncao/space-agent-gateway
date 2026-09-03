package io.github.agncao.spaceagent.gateway.api;

import io.github.agncao.spaceagent.gateway.domain.policy.GatewayPolicyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(GatewayPolicyException.class)
    public ResponseEntity<Map<String, String>> policy(GatewayPolicyException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "protocol_version", "v1",
                "code", exception.getMessage().split(":", 2)[0],
                "message", exception.getMessage()));
    }
}
