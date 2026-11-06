package com.jobradar.score;

import com.jobradar.Fixtures;
import com.jobradar.config.Props;
import com.jobradar.domain.Job;
import com.jobradar.domain.Scored;
import com.jobradar.profile.ProfileStore;
import com.jobradar.store.JobStore;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Against a stub of the Messages API; a real call would need a key and cost money. */
class ScorerTest {
    private static final JsonMapper JSON = new JsonMapper();
    private static final String GOOD = "{\"score\":80,\"reason\":\"fits\",\"visaSponsorship\":\"no\"}";

    private HttpServer api;
    private final JobStore store = mock(JobStore.class);

    @AfterEach
    void stop() { if (api != null) api.stop(0); }

    @Test
    void sendsTheRequestClaudeNeedsAndSavesTheVerdict() throws IOException {
        var body = new AtomicReference<JsonNode>();
        var beta = new AtomicReference<String>();
        var key = new AtomicReference<String>();
        var r = scorer("claude-opus-5-5", Duration.ofSeconds(5), x -> {
            body.set(JSON.readTree(x.getRequestBody().readAllBytes()));
            beta.set(x.getRequestHeaders().getFirst("anthropic-beta"));
            key.set(x.getRequestHeaders().getFirst("Authorization"));
            reply(x, 200, message(GOOD, "end_turn"));
        }).score(jobs(1));

        assertEquals(new Scorer.Outcome(1, 0, null), r);
        var req = body.get();
        assertEquals("claude-opus-5-5", req.path("model").asString());
        assertEquals("Bearer test-key", key.get());
        assertTrue(req.path("system").path(0).path("text").asString().contains("RESUME:\nresume"));
        assertEquals("ephemeral", req.path("system").path(0).path("cache_control").path("type").asString());
        assertTrue(req.path("messages").path(0).path("content").toString().contains("job0"));
        assertEquals("low", req.path("output_config").path("effort").asString());
        assertEquals("json_schema", req.path("output_config").path("format").path("type").asString());
        assertEquals("default", req.path("fallbacks").asString());
        assertTrue(beta.get().contains("server-side-fallback-2026-07-01"), beta.get());
        assertTrue(req.path("temperature").isMissingNode());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Scored>> saved = ArgumentCaptor.forClass(List.class);
        verify(store).saveScores(saved.capture());
        var s = saved.getValue().getFirst();
        assertEquals(80, s.score());
        assertEquals("fits", s.reason());
        assertEquals("no", s.visaSponsorship());
    }

    @Test
    void haikuGetsNoFallback() throws IOException {
        var body = new AtomicReference<JsonNode>();
        scorer("claude-haiku-5-5", Duration.ofSeconds(5), x -> {
            body.set(JSON.readTree(x.getRequestBody().readAllBytes()));
            reply(x, 200, message(GOOD, "end_turn"));
        }).score(jobs(1));
        assertTrue(body.get().path("fallbacks").isMissingNode());
    }

    @Test
    void someJobsFailingIsReportedButNotAllFailed() throws IOException {
        var r = scorer(Duration.ofSeconds(5), x -> {
            if (new String(x.getRequestBody().readAllBytes()).contains("job0")) reply(x, 400, error());
            else reply(x, 200, message(GOOD, "end_turn"));
        }).score(jobs(3));
        assertEquals(2, r.scored());
        assertFalse(r.allFailed());
        assertTrue(r.problem().startsWith("1 of 3 jobs not scored"), r.problem());
    }

    @Test
    void refusalCountsAsFailure() throws IOException {
        var r = scorer(Duration.ofSeconds(5), x -> reply(x, 200, message("", "refusal"))).score(jobs(1));
        assertTrue(r.allFailed());
        assertTrue(r.problem().contains("refusal"), r.problem());
    }

    @Test
    void unparsableReplyCountsAsFailure() throws IOException {
        assertTrue(scorer(Duration.ofSeconds(5), x -> reply(x, 200, message("not json", "end_turn"))).score(jobs(1)).allFailed());
    }

    @Test
    void cutOffReplyCountsAsFailure() throws IOException {
        var r = scorer(Duration.ofSeconds(5), x -> reply(x, 200, message(GOOD, "max_tokens"))).score(jobs(1));
        assertTrue(r.problem().contains("max_tokens"), r.problem());
    }

    @Test
    void badKeyStopsEarlyInsteadOfTryingEveryJob() throws IOException {
        var calls = new AtomicInteger();
        var r = scorer(Duration.ofSeconds(5), x -> {
            calls.incrementAndGet();
            reply(x, 401, error());
        }).score(jobs(200));
        assertTrue(r.allFailed());
        assertEquals(200, r.failed());
        assertTrue(calls.get() <= 12, calls + " calls");   // GIVE_UP, plus what was already in flight
    }

    @Test
    void hungApiTimesOut() throws IOException {
        var r = scorer(Duration.ofMillis(200), x -> {
            try { Thread.sleep(10_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }).score(jobs(1));
        assertTrue(r.allFailed());
        assertTrue(r.problem().contains("First error:"), r.problem());
    }

    @Test
    void noJobsIsNoProblem() throws IOException {
        var r = scorer(Duration.ofSeconds(5), x -> reply(x, 200, message(GOOD, "end_turn"))).score(List.of());
        assertFalse(r.allFailed());
        assertNull(r.problem());
    }

    private interface Api { void handle(HttpExchange x) throws IOException; }

    private Scorer scorer(Duration timeout, Api handler) throws IOException {
        return scorer("test-model", timeout, handler);
    }

    private Scorer scorer(String model, Duration timeout, Api handler) throws IOException {
        api = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        api.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        api.createContext("/v1/messages", handler::handle);
        api.start();
        var profiles = mock(ProfileStore.class);
        when(profiles.get()).thenReturn(Fixtures.PROFILE);
        when(profiles.resume()).thenReturn("resume");
        var props = new Props("test-key", "http://127.0.0.1:" + api.getAddress().getPort(), model, timeout,
                null, null, null, null);
        return new Scorer(props, store, profiles);
    }

    private static List<Job> jobs(int n) {
        return IntStream.range(0, n).mapToObj(i -> new Job("id" + i, "co", "job" + i, "l", "u", "d", null)).toList();
    }

    private static String message(String text, String stopReason) {
        return JSON.writeValueAsString(Map.of("id", "msg_1", "type", "message", "role", "assistant", "model", "m",
                "content", text.isEmpty() ? List.of() : List.of(Map.of("type", "text", "text", text)),
                "stop_reason", stopReason, "usage", Map.of("input_tokens", 1, "output_tokens", 1)));
    }

    private static String error() {
        return "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"nope\"}}";
    }

    private static void reply(HttpExchange x, int status, String body) throws IOException {
        var bytes = body.getBytes();
        x.getResponseHeaders().set("Content-Type", "application/json");
        x.sendResponseHeaders(status, bytes.length);
        try (var out = x.getResponseBody()) { out.write(bytes); }
    }
}
