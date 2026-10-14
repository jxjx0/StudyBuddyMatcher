package com.studybuddy.group;

import static org.junit.jupiter.api.Assertions.*;

import com.studybuddy.group.client.StudentClient;
import com.studybuddy.group.service.GroupException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;

class StudentClientTest {
  private HttpServer server;
  private StudentClient client;

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    client =
        new StudentClient(
            RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort());
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private void respond(String path, int status, String body) {
    server.createContext(
        path,
        exchange -> {
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, bytes.length);
          try (var out = exchange.getResponseBody()) {
            out.write(bytes);
          }
        });
  }

  @Test
  void publicProfilesAreMappedAndMissingProfilesReturnNull() {
    respond(
        "/api/students/known",
        200,
        "{\"userId\":\"known\",\"name\":\"Alice\",\"accountStatus\":\"ACTIVE\"}");
    respond("/api/students/missing", 404, "{}");
    assertEquals("Alice", client.findStudent("known").getName());
    assertNull(client.findStudent("missing"));
  }

  @Test
  void serverErrorsAndInvalidBodiesBecomeUnavailable() {
    respond("/api/students", 503, "{}");
    respond("/api/courses/IS442", 200, "not-json");
    assertEquals(503, assertThrows(GroupException.class, client::students).getStatus().value());
    assertEquals(
        503,
        assertThrows(GroupException.class, () -> client.findCourse("IS442")).getStatus().value());
  }

  @Test
  void connectionFailureBecomesUnavailable() {
    server.stop(0);
    assertEquals(
        503,
        assertThrows(GroupException.class, () -> client.findStudent("known")).getStatus().value());
  }
}
