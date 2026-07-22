package com.jobseekercopilot.locationgateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

final class ProviderHttpStub implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor;
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private volatile Function<RecordedRequest, StubResponse> responder =
            request -> StubResponse.json(500, "{\"message\":\"unconfigured test stub\"}");

    private ProviderHttpStub(HttpServer server, ExecutorService executor) {
        this.server = server;
        this.executor = executor;
    }

    static ProviderHttpStub start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "location-provider-test-stub");
                thread.setDaemon(true);
                return thread;
            });
            ProviderHttpStub stub = new ProviderHttpStub(server, executor);
            server.setExecutor(executor);
            server.createContext("/", stub::handle);
            server.start();
            return stub;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start loopback provider test stub.", exception);
        }
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void reset() {
        requests.clear();
        responder = request -> StubResponse.json(500, "{\"message\":\"unconfigured test stub\"}");
    }

    void respond(Function<RecordedRequest, StubResponse> responseFunction) {
        responder = responseFunction;
    }

    void enqueue(StubResponse... responses) {
        if (responses.length == 0) throw new IllegalArgumentException("At least one response is required.");
        AtomicInteger index = new AtomicInteger();
        responder = request -> responses[Math.min(index.getAndIncrement(), responses.length - 1)];
    }

    List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    int requestCount() {
        return requests.size();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        RecordedRequest request = new RecordedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getRawPath(),
                exchange.getRequestURI().getRawQuery(),
                Map.copyOf(exchange.getRequestHeaders()));
        requests.add(request);
        StubResponse response = responder.apply(request);

        if (!response.delay().isZero()) {
            try {
                Thread.sleep(response.delay().toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                exchange.close();
                return;
            }
        }
        if (response.disconnect()) {
            exchange.close();
            return;
        }

        byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        try {
            exchange.sendResponseHeaders(response.status(), body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }

    record RecordedRequest(
            String method,
            String rawPath,
            String rawQuery,
            Map<String, List<String>> headers) {
        String firstHeader(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .map(Map.Entry::getValue)
                    .filter(values -> !values.isEmpty())
                    .map(values -> values.get(0))
                    .findFirst()
                    .orElse(null);
        }
    }

    record StubResponse(int status, String body, Duration delay, boolean disconnect) {
        static StubResponse json(int status, String body) {
            return new StubResponse(status, body, Duration.ZERO, false);
        }

        static StubResponse delayedJson(int status, String body, Duration delay) {
            return new StubResponse(status, body, delay, false);
        }

        static StubResponse connectionDrop() {
            return new StubResponse(0, "", Duration.ZERO, true);
        }
    }
}
