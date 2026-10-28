package cn.communityelderly.common.idempotency;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(IdempotencyIntegrationTest.IdempotencyTestController.class)
class IdempotencyIntegrationTest {
    private static final AtomicInteger INVOCATIONS = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void resetInvocationCount() {
        INVOCATIONS.set(0);
        IdempotencyTestController.requestStarted = null;
        IdempotencyTestController.releaseRequest = null;
    }

    @Test
    @DisplayName("completed key replays original HTTP status and JSON body for canonical-equivalent requests")
    void completedKeyReplaysStatusAndBodyWhenJsonObjectKeyOrderChanges() throws Exception {
        String key = "integration-" + UUID.randomUUID();
        var first = mockMvc.perform(post("/api/test/idempotency?channel=care")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"b\":2,\"a\":1}"))
                .andReturn().getResponse();
        var replay = mockMvc.perform(post("/api/test/idempotency?channel=care")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"a\" : 1, \"b\" : 2 }"))
                .andReturn().getResponse();
        var changedBody = mockMvc.perform(post("/api/test/idempotency?channel=care")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"a\":9,\"b\":2}"))
                .andReturn().getResponse();
        var afterConflict = mockMvc.perform(post("/api/test/idempotency?channel=care")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"a\" : 1, \"b\" : 2 }"))
                .andReturn().getResponse();

        assertEquals(201, first.getStatus());
        assertEquals(201, replay.getStatus());
        assertEquals(first.getContentAsString(), replay.getContentAsString());
        assertEquals(409, changedBody.getStatus());
        assertEquals(201, afterConflict.getStatus());
        assertEquals(first.getContentAsString(), afterConflict.getContentAsString());
        assertEquals(1, INVOCATIONS.get());
    }

    @Test
    @DisplayName("in-flight duplicate returns conflict without replacing the owner's result")
    void concurrentDuplicateDoesNotExecuteOrOverwriteTheOwner() throws Exception {
        String key = "concurrent-" + UUID.randomUUID();
        CountDownLatch firstRequestStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstRequest = new CountDownLatch(1);
        IdempotencyTestController.requestStarted = firstRequestStarted;
        IdempotencyTestController.releaseRequest = releaseFirstRequest;
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            CompletableFuture<org.springframework.mock.web.MockHttpServletResponse> firstRequest =
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            return mockMvc.perform(post("/api/test/idempotency/concurrent")
                                            .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, key)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content("{\"action\":\"hold\"}"))
                                    .andReturn().getResponse();
                        } catch (Exception exception) {
                            throw new CompletionException(exception);
                        }
                    }, executor);

            assertTrue(firstRequestStarted.await(5, TimeUnit.SECONDS), "first request did not reach the controller");
            var concurrentDuplicate = mockMvc.perform(post("/api/test/idempotency/concurrent")
                            .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"action\":\"hold\"}"))
                    .andReturn().getResponse();

            assertEquals(409, concurrentDuplicate.getStatus());
            assertEquals(1, INVOCATIONS.get());

            releaseFirstRequest.countDown();
            var first = firstRequest.get(10, TimeUnit.SECONDS);
            var replay = mockMvc.perform(post("/api/test/idempotency/concurrent")
                            .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"action\":\"hold\"}"))
                    .andReturn().getResponse();

            assertEquals(201, first.getStatus());
            assertEquals(201, replay.getStatus());
            assertEquals(first.getContentAsString(), replay.getContentAsString());
            assertEquals(1, INVOCATIONS.get());
        } finally {
            releaseFirstRequest.countDown();
            executor.shutdownNow();
            IdempotencyTestController.requestStarted = null;
            IdempotencyTestController.releaseRequest = null;
        }
    }

    @Test
    @DisplayName("failed JSON responses replay the original failure status and body")
    void failedJsonResponseDoesNotReplayAsSuccessOrExecuteAgain() throws Exception {
        String key = "failure-" + UUID.randomUUID();
        var first = mockMvc.perform(post("/api/test/idempotency/failure")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"invalid\"}"))
                .andReturn().getResponse();
        var replay = mockMvc.perform(post("/api/test/idempotency/failure")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"invalid\"}"))
                .andReturn().getResponse();

        assertEquals(422, first.getStatus());
        assertEquals(422, replay.getStatus());
        assertEquals(first.getContentAsString(), replay.getContentAsString());
        assertEquals(1, INVOCATIONS.get());
    }

    @RestController
    @RequestMapping("/api/test/idempotency")
    static class IdempotencyTestController {
        private static volatile CountDownLatch requestStarted;
        private static volatile CountDownLatch releaseRequest;

        @PostMapping
        @Idempotency(required = true)
        ResponseEntity<Map<String, Object>> create(@RequestBody JsonNode body) {
            int invocation = INVOCATIONS.incrementAndGet();
            return ResponseEntity.status(201).body(Map.of(
                    "invocation", invocation,
                    "fieldCount", body.size(),
                    "displayName", "药盒提醒"));
        }

        @PostMapping("/failure")
        @Idempotency(required = true)
        ResponseEntity<Map<String, Object>> fail(@RequestBody JsonNode body) {
            int invocation = INVOCATIONS.incrementAndGet();
            return ResponseEntity.unprocessableEntity().body(Map.of(
                    "code", "VALIDATION_FAILED", "reason", body.path("reason").asText(), "invocation", invocation));
        }

        @PostMapping("/concurrent")
        @Idempotency(required = true)
        ResponseEntity<Map<String, Object>> waitForConcurrentRequest(@RequestBody JsonNode body) throws InterruptedException {
            int invocation = INVOCATIONS.incrementAndGet();
            CountDownLatch started = requestStarted;
            CountDownLatch release = releaseRequest;
            if (started != null && release != null) {
                started.countDown();
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("concurrency test release timed out");
                }
            }
            return ResponseEntity.status(201).body(Map.of(
                    "invocation", invocation,
                    "action", body.path("action").asText(),
                    "message", "并发请求完成"));
        }
    }
}
