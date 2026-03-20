/*
 * Copyright 2008-2026 Async-IO.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package ai.javaclaw.channels.atmosphere;

import java.io.IOException;
import java.util.Map;

import ai.javaclaw.channels.ChannelMessageReceivedEvent;
import ai.javaclaw.channels.ChannelRegistry;
import org.atmosphere.cpr.AtmosphereHandler;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceEvent;
import org.atmosphere.cpr.AtmosphereResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.util.HtmlUtils;

import tools.jackson.databind.ObjectMapper;

/**
 * Atmosphere handler that streams AI responses token-by-token.
 * <p>
 * Replaces JavaClaw's {@code ChatWebSocketHandler} and {@code WebSocketConfig}.
 * Uses the same {@link ChatClient} bean (with all advisors, tools, and memory)
 * but calls {@code .stream()} instead of {@code .call()}, delivering each token
 * to the browser as it arrives — like ChatGPT, Claude, and other modern AI chats.
 * <p>
 * Supports WebSocket, SSE, and long-polling transports transparently.
 */
public class AtmosphereChatHandler implements AtmosphereHandler {

    private static final Logger log = LoggerFactory.getLogger(AtmosphereChatHandler.class);

    private final ChatClient chatClient;
    private final ChannelRegistry channelRegistry;
    private final ObjectMapper objectMapper;

    public AtmosphereChatHandler(ChatClient chatClient, ChannelRegistry channelRegistry,
                                 ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.channelRegistry = channelRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onRequest(AtmosphereResource resource) throws IOException {
        String body = resource.getRequest().body().asString();

        if (body != null && !body.isBlank()) {
            handleMessage(resource, body);
        } else {
            resource.suspend();
        }
    }

    @Override
    public void onStateChange(AtmosphereResourceEvent event) throws IOException {
        if (event.isCancelled() || event.isResumedOnTimeout()) {
            return;
        }

        AtmosphereResponse response = event.getResource().getResponse();
        Object message = event.getMessage();
        if (message != null) {
            response.write(message.toString());
            response.flushBuffer();
        }
    }

    @Override
    public void destroy() {
    }

    @SuppressWarnings("unchecked")
    private void handleMessage(AtmosphereResource resource, String body) throws IOException {
        Map<String, Object> payload = objectMapper.readValue(body, Map.class);
        String userMessage = (String) payload.get("message");

        if (userMessage == null || userMessage.isBlank()) {
            return;
        }
        userMessage = userMessage.trim();

        // Track this channel as the latest for background task routing
        channelRegistry.publishMessageReceivedEvent(
                new ChannelMessageReceivedEvent(AtmosphereChatChannel.CHANNEL_NAME, userMessage));

        AtmosphereResponse response = resource.getResponse();

        // Send user bubble + typing indicator (client creates the streaming
        // bubble when the first token arrives, replacing the typing dots)
        response.write(
                oobAppend("chat-messages", userBubble(userMessage)) +
                oobReplace("typing-indicator", typingDots())
        );
        response.flushBuffer();

        // Stream the AI response token-by-token using the same ChatClient bean
        // (same advisors, tools, memory — just .stream() instead of .call())
        try {
            chatClient.prompt(userMessage)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, ChatMemory.CONVERSATION_ID))
                    .stream()
                    .content()
                    .doOnNext(token -> writeToken(response, token))
                    .doOnComplete(() -> writeStreamEnd(response))
                    .doOnError(err -> {
                        log.error("Streaming error: {}", err.getMessage());
                        writeToken(response, "\n\n[Error: " + err.getMessage() + "]");
                        writeStreamEnd(response);
                    })
                    .blockLast();
        } catch (Exception e) {
            log.error("Failed to start streaming: {}", e.getMessage());
            writeToken(response, "Sorry, I encountered an error: " + e.getMessage());
            writeStreamEnd(response);
        }
    }

    private void writeToken(AtmosphereResponse response, String token) {
        try {
            response.write("{\"token\":" + jsonEscape(token) + "}");
            response.flushBuffer();
        } catch (IOException e) {
            log.warn("Failed to write streaming token: {}", e.getMessage());
        }
    }

    private void writeStreamEnd(AtmosphereResponse response) {
        try {
            response.write("{\"done\":true}");
            response.write(oobReplace("typing-indicator", ""));
            response.flushBuffer();
        } catch (IOException e) {
            log.warn("Failed to write stream completion: {}", e.getMessage());
        }
    }

    private static String jsonEscape(String text) {
        var sb = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append("\"").toString();
    }

    // ---- HTML helpers ----

    static String userBubble(String text) {
        return "<article class=\"ar-msg ar-msg--user\">" +
                "<div class=\"ar-msg__bubble\">" + escape(text) + "</div>" +
                "</article>";
    }

    static String streamingAgentBubble() {
        return "<article class=\"ar-msg ar-msg--agent\">" +
                "<div class=\"ar-msg__avatar\">JC</div>" +
                "<div class=\"ar-msg__bubble\" id=\"streaming-bubble\"></div>" +
                "</article>";
    }

    private static String typingDots() {
        return "<div class=\"ar-typing\">" +
                "<div class=\"ar-msg__avatar\">JC</div>" +
                "<div class=\"ar-typing__dots\"><span></span><span></span><span></span></div>" +
                "</div>";
    }

    static String oobAppend(String id, String content) {
        return "<div id=\"" + id + "\" hx-swap-oob=\"beforeend\">" + content + "</div>";
    }

    static String oobReplace(String id, String content) {
        return "<div id=\"" + id + "\" hx-swap-oob=\"true\">" + content + "</div>";
    }

    private static String escape(String text) {
        return HtmlUtils.htmlEscape(text);
    }
}
