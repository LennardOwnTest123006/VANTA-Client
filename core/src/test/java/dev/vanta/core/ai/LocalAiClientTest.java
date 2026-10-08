package dev.vanta.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocalAiClientTest {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    LocalFileServer server;

    @BeforeEach
    void start() throws IOException {
        server = new LocalFileServer();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void requestBodyHasTheNexusShape() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        LocalAiClient.ChatRequest request = LocalAiClient.ChatRequest.of(List.of(
                LocalAiClient.Message.system("sys"), LocalAiClient.Message.user("hello")), schema);
        JsonObject body = LocalAiClient.body(request);

        assertEquals(0.2, body.get("temperature").getAsDouble(), 1e-9);
        assertEquals(512, body.get("max_tokens").getAsInt());
        assertFalse(body.get("stream").getAsBoolean());
        assertEquals(2, body.getAsJsonArray("messages").size());
        assertEquals("system", body.getAsJsonArray("messages").get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("hello", body.getAsJsonArray("messages").get(1).getAsJsonObject().get("content").getAsString());
        JsonObject format = body.getAsJsonObject("response_format");
        assertEquals("json_schema", format.get("type").getAsString());
        assertEquals("nexus", format.getAsJsonObject("json_schema").get("name").getAsString());
        assertEquals("object", format.getAsJsonObject("json_schema").getAsJsonObject("schema").get("type")
                .getAsString());
        assertFalse(body.getAsJsonObject("chat_template_kwargs").get("enable_thinking").getAsBoolean());

        LocalAiClient.ChatRequest free = new LocalAiClient.ChatRequest(List.of(LocalAiClient.Message.user("x")),
                Optional.empty(), 0.7, 64);
        assertFalse(LocalAiClient.body(free).has("response_format"));
        assertThrows(IllegalArgumentException.class, () -> LocalAiClient.ChatRequest.of(List.of(), schema));
    }

    @Test
    void parsesCompletionsAndTheirContent() throws LocalAiException {
        JsonObject completion = JsonParser.parseString("""
                {"choices":[{"finish_reason":"stop","message":{"role":"assistant",
                 "content":"{\\"message\\":\\"hi\\",\\"actions\\":[]}","reasoning_content":"thought"}}],
                 "usage":{"prompt_tokens":12,"completion_tokens":7}}
                """).getAsJsonObject();
        LocalAiClient.ChatResponse response = LocalAiClient.parse(completion);
        assertEquals("hi", response.contentAsJson().get("message").getAsString());
        assertEquals("thought", response.reasoning().orElseThrow());
        assertEquals("stop", response.finishReason());
        assertEquals(12, response.promptTokens());
        assertEquals(7, response.completionTokens());

        LocalAiClient.ChatResponse fenced = new LocalAiClient.ChatResponse(
                "<think>hmm</think>\n```json\n{\"message\":\"x\",\"actions\":[]}\n```", Optional.empty(), "", 0, 0);
        assertEquals("x", fenced.contentAsJson().get("message").getAsString());

        LocalAiClient.ChatResponse broken = new LocalAiClient.ChatResponse("not json", Optional.empty(), "", 0, 0);
        assertEquals(LocalAiException.Kind.INVALID_RESPONSE,
                assertThrows(LocalAiException.class, broken::contentAsJson).kind());
        LocalAiClient.ChatResponse array = new LocalAiClient.ChatResponse("[1]", Optional.empty(), "", 0, 0);
        assertThrows(LocalAiException.class, array::contentAsJson);

        assertThrows(LocalAiException.class, () -> LocalAiClient.parse(JsonParser.parseString(
                "{\"error\":{\"message\":\"boom\"}}").getAsJsonObject()));
        assertThrows(LocalAiException.class, () -> LocalAiClient.parse(JsonParser.parseString(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\"}}]}").getAsJsonObject()));
    }

    @Test
    void talksToTheServerOnTheLoopbackAddress() throws LocalAiException {
        server.json(LocalAiClient.CHAT_PATH, body -> {
            JsonObject sent = JsonParser.parseString(body).getAsJsonObject();
            String question = sent.getAsJsonArray("messages").get(1).getAsJsonObject().get("content").getAsString();
            return "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":"
                    + "\"{\\\"message\\\":\\\"echo " + question + "\\\",\\\"actions\\\":[]}\"}}]}";
        });
        LocalAiClient client = new LocalAiClient(HTTP, LocalAiClient.loopback(server.port()), Duration.ofSeconds(10));
        assertEquals(URI.create("http://127.0.0.1:" + server.port()), client.base());
        assertTrue(client.health());
        assertEquals(200, client.healthStatus());
        LocalAiClient.ChatResponse response = client.chat(LocalAiClient.ChatRequest.of(List.of(
                LocalAiClient.Message.system("s"), LocalAiClient.Message.user("ping")), new JsonObject()));
        assertEquals("echo ping", response.contentAsJson().get("message").getAsString());
        JsonObject sent = JsonParser.parseString(server.bodies.get(0)).getAsJsonObject();
        assertEquals("json_schema", sent.getAsJsonObject("response_format").get("type").getAsString());
        assertFalse(sent.getAsJsonObject("chat_template_kwargs").get("enable_thinking").getAsBoolean());
        assertEquals(0.2, sent.get("temperature").getAsDouble(), 1e-9);
    }

    @Test
    void serverErrorsAndMissingServersAreDistinct() {
        server.fail(LocalAiClient.CHAT_PATH, 500);
        server.fail(LocalAiClient.HEALTH_PATH, 503);
        LocalAiClient client = new LocalAiClient(HTTP, LocalAiClient.loopback(server.port()), Duration.ofSeconds(10));
        assertEquals(503, client.healthStatus());
        assertFalse(client.health());
        LocalAiException http = assertThrows(LocalAiException.class, () -> client.chat(LocalAiClient.ChatRequest.of(
                List.of(LocalAiClient.Message.user("x")), new JsonObject())));
        assertEquals(LocalAiException.Kind.HTTP, http.kind());

        int closedPort = server.port();
        server.close();
        LocalAiClient gone = new LocalAiClient(HTTP, LocalAiClient.loopback(closedPort), Duration.ofSeconds(5));
        assertEquals(-1, gone.healthStatus());
        LocalAiException network = assertThrows(LocalAiException.class, () -> gone.chat(LocalAiClient.ChatRequest.of(
                List.of(LocalAiClient.Message.user("x")), new JsonObject())));
        assertEquals(LocalAiException.Kind.NETWORK, network.kind());
    }
}
