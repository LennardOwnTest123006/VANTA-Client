package dev.vanta.core.ai;

import java.util.function.Consumer;

/**
 * Asynchronous chat: the request runs off the render thread and exactly one of the two callbacks is invoked later on
 * the main-thread executor. {@link LocalAiRuntime} and {@link LocalAiService} implement it; tests use fakes.
 */
public interface ChatBackend {
    /**
     * Sends a chat completion request.
     *
     * @param request the messages, schema and sampling settings
     * @param onReply receives the parsed reply (main thread)
     * @param onError receives the failure (main thread)
     */
    void chat(LocalAiClient.ChatRequest request, Consumer<LocalAiClient.ChatResponse> onReply,
              Consumer<LocalAiException> onError);
}
