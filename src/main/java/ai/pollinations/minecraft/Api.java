package ai.pollinations.minecraft;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

/**
 * HTTP calls to gen.pollinations.ai. Blocking I/O runs on the plugin's own pool, never on the server thread.
 * The API key is sent only in the Authorization header and is never logged.
 */
final class Api {

    static final String BASE = "https://gen.pollinations.ai";
    static final int IMAGE_REQUEST_PX = 512;

    private static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;
    private static final String NETWORK_ERROR = "Could not reach Pollinations. Try again in a moment.";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final ExecutorService pool;
    private final Logger log;

    Api(ExecutorService pool, Logger log) {
        this.pool = pool;
        this.log = log;
    }

    CompletableFuture<Result<String>> chat(String apiKey, String model, String body) {
        return CompletableFuture.supplyAsync(() -> chatBlocking(apiKey, model, body), pool)
                .exceptionally(error -> Result.fail(NETWORK_ERROR));
    }

    CompletableFuture<Result<ImageMaps.Prepared>> image(String apiKey, String model, String prompt) {
        return CompletableFuture.supplyAsync(() -> imageBlocking(apiKey, model, prompt), pool)
                .exceptionally(error -> Result.fail(NETWORK_ERROR));
    }

    /** Public GET with no key, used for the model catalogue. */
    CompletableFuture<Result<String>> get(String path) {
        return CompletableFuture.supplyAsync(() -> getBlocking(path), pool)
                .exceptionally(error -> Result.fail(NETWORK_ERROR));
    }

    static boolean success(int status) {
        return status >= 200 && status < 300;
    }

    /** Player-facing text for a failed status. Never includes the key or the response body. */
    static String friendlyError(int status) {
        return switch (status) {
            case 401, 403 -> "The Pollinations API key was rejected. The server owner should check config.yml.";
            case 402 -> "The Pollinations account is out of Pollen. The server owner should top up at enter.pollinations.ai.";
            case 429 -> "Too many requests to Pollinations right now. Try again in a minute.";
            default -> "Pollinations is unavailable right now (HTTP " + status + "). Try again later.";
        };
    }

    private Result<String> chatBlocking(String apiKey, String model, String body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + "/v1/chat/completions"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            long started = System.nanoTime();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("text request model=" + model + " status=" + response.statusCode()
                    + " ms=" + elapsedMillis(started));
            if (!success(response.statusCode())) {
                return Result.fail(friendlyError(response.statusCode()));
            }
            Optional<String> reply = ChatCodec.parseReply(response.body());
            return reply.map(text -> Result.ok(text))
                    .orElseGet(() -> Result.fail("Pollinations sent an empty reply. Try again."));
        } catch (IOException e) {
            return Result.fail(NETWORK_ERROR);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.fail(NETWORK_ERROR);
        }
    }

    private Result<ImageMaps.Prepared> imageBlocking(String apiKey, String model, String prompt) {
        URI uri = URI.create(BASE + "/image/" + encode(prompt)
                + "?model=" + encode(model) + "&width=" + IMAGE_REQUEST_PX + "&height=" + IMAGE_REQUEST_PX);
        try {
            long started = System.nanoTime();
            HttpResponse<byte[]> response = get(uri, apiKey);
            if (isRedirect(response.statusCode())) {
                // One hop, https only, and without the key: the key must never leave gen.pollinations.ai.
                Optional<URI> next = redirectTarget(uri, response);
                if (next.isEmpty()) {
                    return Result.fail(friendlyError(response.statusCode()));
                }
                response = get(next.get(), null);
            }
            log.info("image request model=" + model + " status=" + response.statusCode()
                    + " ms=" + elapsedMillis(started));
            if (!success(response.statusCode())) {
                return Result.fail(friendlyError(response.statusCode()));
            }
            String type = response.headers().firstValue("Content-Type").orElse("");
            if (!type.startsWith("image/jpeg") && !type.startsWith("image/png")) {
                return Result.fail("This image model returned " + type + ", which the plugin cannot show. "
                        + "Try another image-model in config.yml.");
            }
            if (response.body().length > MAX_IMAGE_BYTES) {
                return Result.fail("The image Pollinations returned was too large to load.");
            }
            try {
                return Result.ok(ImageMaps.prepare(response.body()));
            } catch (IOException | IllegalArgumentException unreadable) {
                return Result.fail("Could not read the image Pollinations returned. Try a different prompt.");
            }
        } catch (IOException e) {
            return Result.fail(NETWORK_ERROR);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.fail(NETWORK_ERROR);
        }
    }

    private Result<String> getBlocking(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + path))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (!success(response.statusCode())) {
                return Result.fail(friendlyError(response.statusCode()));
            }
            return Result.ok(response.body());
        } catch (IOException e) {
            return Result.fail(NETWORK_ERROR);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.fail(NETWORK_ERROR);
        }
    }

    private HttpResponse<byte[]> get(URI uri, String apiKey) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(120)).GET();
        if (apiKey != null) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static Optional<URI> redirectTarget(URI from, HttpResponse<?> response) {
        Optional<String> location = response.headers().firstValue("Location");
        if (location.isEmpty()) {
            return Optional.empty();
        }
        URI next = from.resolve(location.get());
        return "https".equalsIgnoreCase(next.getScheme()) ? Optional.of(next) : Optional.empty();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }
}
