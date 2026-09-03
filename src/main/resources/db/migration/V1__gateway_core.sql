CREATE TABLE agent_manifest (
    worker VARCHAR(128) PRIMARY KEY,
    service_name VARCHAR(128) NOT NULL,
    agent_version VARCHAR(64) NOT NULL,
    protocol_version VARCHAR(16) NOT NULL,
    manifest_json JSON NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

CREATE TABLE task_delivery (
    execution_id VARCHAR(64) PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    requester_id VARCHAR(128) NOT NULL,
    run_id VARCHAR(128) NOT NULL,
    step_id VARCHAR(128) NOT NULL,
    worker VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    payload_json JSON NOT NULL,
    accepted_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_task_worker FOREIGN KEY (worker) REFERENCES agent_manifest(worker)
);

CREATE TABLE task_event (
    event_id VARCHAR(128) PRIMARY KEY,
    execution_id VARCHAR(64) NOT NULL,
    event_seq BIGINT NOT NULL,
    worker VARCHAR(128) NOT NULL,
    payload_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_task_event_sequence(execution_id, event_seq),
    CONSTRAINT fk_event_task FOREIGN KEY (execution_id) REFERENCES task_delivery(execution_id)
);

CREATE TABLE task_action (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    execution_id VARCHAR(64) NOT NULL,
    action_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    payload_json JSON NOT NULL,
    UNIQUE KEY uk_task_action(execution_id, action_id)
);

CREATE TABLE task_command (
    command_id VARCHAR(128) PRIMARY KEY,
    execution_id VARCHAR(64) NOT NULL,
    type VARCHAR(32) NOT NULL,
    payload_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE outbox_event (
    id VARCHAR(64) PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    topic VARCHAR(128) NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at TIMESTAMP(6) NULL,
    KEY ix_outbox_pending(status, next_attempt_at)
);
