package dev.vanta.core.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Talks to a running {@code llama-server} over its OpenAI-compatible {@code /v1/chat/completions} endpoint on the
 * loopback address. Requests carry the Nexus JSON schema as {@code response_format} (grammar-constrained sampling),
 * {@code chat_template_kwargs.enable_thinking=false}, temperature {@value #TEMPERATURE} and
 * {@value #MAX_TOKENS} max tokens. Blocking; callers run it on a worker thread.
 */
public final class LocalAiClient {
    /** Default request timeout. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(90);
    /** Sampling temperature for deterministic-ish structured replies. */
    public static final double TEMPERATURE = 0.2;
    /** Reply length cap. */
    public static final int MAX_TOKENS = 512;
    /** Name of the JSON schema in {@code response_format}. */
    public static final String SCHEMA_NAME = "nexus";
    /** Chat completions path. */
    public static final String CHAT_PATH = "/v1/chat/completions";
    /** Health path ({@code {"status":"ok"}} with HTTP 200 once the model is loaded). */
    public static final String HEALTH_PATH = "/health";

    /** One chat message. */
    public record Message(String role, String content) {
        public Message {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(content, "content");
        }

        public static Message system(String content) {
            return new Message("system", content);
        }

        public static Message user(String content) {
            return new Message("user", content);
        }

        public static Message assistant(String content) {
            return new Message("assistant", content);
        }

        JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("role", role);
            o.addProperty("content", content);
            return o;
        }
    }

    /**
     * A completion request.
     *
     * @param schema JSON schema the reply must follow (empty for free text)
     */
    public record ChatRequest(List<Message> messages, Optional<JsonObject> schema, double temperature, int maxTokens) {
        public ChatRequest {
            messages = List.copyOf(messages);
            Objects.requireNonNull(schema, "schema");
            if (messages.isEmpty()) {
                throw new IllegalArgumentException("a chat request needs at least one message");
            }
        }

        /** Request with the Nexus defaults. */
        public static ChatRequest of(List<Message> messages, JsonObject schema) {
            return new ChatRequest(messages, Optional.of(schema), TEMPERATURE, MAX_TOKENS);
        }
    }

    /**
     * A parsed completion.
     *
     * @param content   the assistant message (JSON text when a schema was sent)
     * @param reasoning {@code reasoning_content} when the server returned any
     */
    public record ChatResponse(String content, Optional<String> reasoning, String finishReason, int promptTokens,
                               int completionTokens) {
        public ChatResponse {
            Objects.requireNonNull(content, "content");
            Objects.requireNonNull(reasoning, "reasoning");
            Objects.requireNonNull(finishReason, "finishReason");
        }

        /**
         * The content as a JSON object; tolerates a {@code <think>...</think>} prefix and Markdown code fences.
         *
         * @throws LocalAiException {@code INVALID_RESPONSE} when it is not a JSON object
         */
        public JsonObject contentAsJson() throws LocalAiException {
            String text = content.trim();
            int think = text.indexOf("</think>");
            if (think >= 0) {
                text = text.substring(think + "</think>".length()).trim();
            }
            if (text.startsWith("```")) {
                int firstBreak = text.indexOf('\n');
                text = firstBreak >= 0 ? text.substring(firstBreak + 1) : "";
                int fence = text.lastIndexOf("```");
                if (fence >= 0) {
                    text = text.substring(0, fence);
                }
                text = text.trim();
            }
            try {
                JsonElement element = JsonParser.parseString(text);
                if (!element.isJsonObject()) {
                    throw new LocalAiException(LocalAiException.Kind.INVALID_RESPONSE, "reply is not a JSON object");
                }
                return element.getAsJsonObject();
            } catch (JsonParseException e) {
                throw new LocalAiException(LocalAiException.Kind.INVALID_RESPONSE, "reply is not valid JSON", e);
            }
        }
    }

    private final HttpClient http;
    private final URI base;
    private final Duration timeout;

    /**
     * @param base the server root, e.g. {@code http://127.0.0.1:8080}
     */
    public LocalAiClient(HttpClient http, URI base, Duration timeout) {
        this.http = Objects.requireNonNull(http, "http");
        this.base = Objects.requireNonNull(base, "base");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    /** Root URI of a server on the loopback address. */
    public static URI loopback(int port) {
        return URI.create("http://127.0.0.1:" + port);
    }

    public URI base() {
        return base;
    }

    /** HTTP status of {@code GET /health}, or -1 when the server does not answer. */
    public int healthStatus() {
        HttpRequest request = HttpRequest.newBuilder(base.resolve(HEALTH_PATH)).GET()
                .timeout(Duration.ofSeconds(5)).build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException e) {
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    /** True when the server answers {@code /health} with 200. */
    public boolean health() {
        return healthStatus() == 200;
    }

    /** Sends a chat completion and parses the reply. */
    public ChatResponse chat(ChatRequest request) throws LocalAiException {
        Objects.requireNonNull(request, "request");
        String body = body(request).toString();
        HttpRequest httpRequest = HttpRequest.newBuilder(base.resolve(CHAT_PATH))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        HttpResponse<String> response;
        try {
            response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.net.http.HttpTimeoutException e) {
            throw new LocalAiException(LocalAiException.Kind.NETWORK, "the Local AI did not answer within "
                    + timeout.toSeconds() + " s", e);
        } catch (IOException e) {
            throw new LocalAiException(LocalAiException.Kind.NETWORK, "could not reach the Local AI: "
                    + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LocalAiException(LocalAiException.Kind.CANCELLED, "interrupted", e);
        }
        if (response.statusCode() != 200) {
            throw new LocalAiException(LocalAiException.Kind.HTTP, "the Local AI answered HTTP "
                    + response.statusCode() + ": " + abbreviate(response.body()));
        }
        try {
            JsonElement element = JsonParser.parseString(response.body());
            if (!element.isJsonObject()) {
                throw new LocalAiException(LocalAiException.Kind.INVALID_RESPONSE, "completion is not a JSON object");
            }
            return parse(element.getAsJsonObject());
        } catch (JsonParseException e) {
            throw new LocalAiException(LocalAiException.Kind.INVALID_RESPONSE, "completion is not valid JSON", e);
        }
    }

    /** The request body sent to {@code /v1/chat/completions}. */
    public static JsonObject body(ChatRequest request) {
        JsonObject body = new JsonObject();
        JsonArray messages = new JsonArray();
        for (Message message : request.messages()) {
            messages.add(message.toJson());
        }
        body.add("messages", messages);
        body.addProperty("temperature", request.temperature());
        body.addProperty("max_tokens", request.maxTokens());
        body.addProperty("stream", false);
        request.schema().ifPresent(schema -> {
            JsonObject format = new JsonObject();
            format.addProperty("type", "json_schema");
            JsonObject named = new JsonObject();
            named.addProperty("name", SCHEMA_NAME);
            named.add("schema", schema.deepCopy());
            format.add("json_schema", named);
            body.add("response_format", format);
        });
        JsonObject templateKwargs = new JsonObject();
        templateKwargs.addProperty("enable_thinking", false);
        body.add("chat_template_kwargs", templateKwargs);
        return body;
    }

    /** Parses an OpenAI-style completion object. */
    public static ChatResponse parse(JsonObject completion) throws LocalAiException {
        JsonElement choices = completion.get("choices");
        if (choices == null || !choices.isJsonArray() || choices.getAsJsonArray().isEmpty()) {
            JsonElement error = completion.get("error");
            String detail = error == null ? "no choices" : abbreviate(error.toString());
            throw new LocalAiException(LocalAiException.Kind.INVALID_RESPONSE, "completion without choices: "
                    + detail);
        }
        JsonElement first = choices.getAsJsonArray().get(0);
        if (!first.isJsonObject()) {
            throw new LocalAiException(LocalAiException.Kind.INVALID_RESPONSE, "choice is not an object");
        }
        JsonObject choice = first.getAsJsonObject();
        JsonElement messageElement = choice.get("message");
        if (messageElement == null || !messageElement.isJsonObject()) {
            throw new LocalAiException(LocalAiException.Kind.INVALID_RESPONSE, "choice without message");
        }
        JsonObject message = messageElement.getAsJsonObject();
        String content = string(message, "content");
        if (content == null) {
            throw new LocalAiException(LocalAiException.Kind.INVALID_RESPONSE, "message without content");
        }
        Optional<String> reasoning = Optional.ofNullable(string(message, "reasoning_content"));
        String finish = Optional.ofNullable(string(choice, "finish_reason")).orElse("");
        int prompt = 0;
        int completionTokens = 0;
        JsonElement usage = completion.get("usage");
        if (usage != null && usage.isJsonObject()) {
            prompt = integer(usage.getAsJsonObject(), "prompt_tokens");
            completionTokens = integer(usage.getAsJsonObject(), "completion_tokens");
        }
        return new ChatResponse(content, reasoning, finish, prompt, completionTokens);
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
    }

    private static int integer(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsInt() : 0;
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 200 ? oneLine.substring(0, 200) + "..." : oneLine;
    }
}
