package io.github.agncao.spaceagent.gateway.integration;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.wait.strategy.Wait;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class RocketMqIT {
    @Container
    static final GenericContainer<?> NAMESERVER = new GenericContainer<>(DockerImageName.parse("apache/rocketmq:5.3.2"))
            .withCommand("sh", "mqnamesrv")
            .withExposedPorts(9876)
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(45)));

    @Test
    void startsPinnedRocketMqNameserver() throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(
                    new InetSocketAddress(NAMESERVER.getHost(), NAMESERVER.getMappedPort(9876)),
                    (int) Duration.ofSeconds(3).toMillis());
            assertThat(socket.isConnected()).isTrue();
        }
    }
}
