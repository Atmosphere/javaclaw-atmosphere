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

import org.atmosphere.config.service.AtmosphereHandlerService;
import org.atmosphere.cpr.AtmosphereHandler;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceEvent;
import org.atmosphere.cpr.AtmosphereResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.HtmlUtils;

import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Atmosphere handler that processes chat messages from the web UI.
 * <p>
 * Replaces JavaClaw's {@code ChatWebSocketHandler} and {@code WebSocketConfig}.
 * Supports WebSocket, SSE, and long-polling transports transparently.
 */
public class AtmosphereChatHandler implements AtmosphereHandler {

    private static final Logger log = LoggerFactory.getLogger(AtmosphereChatHandler.class);

    private final AtmosphereChatChannel chatChannel;
    private final ObjectMapper objectMapper;

    public AtmosphereChatHandler(AtmosphereChatChannel chatChannel, ObjectMapper objectMapper) {
        this.chatChannel = chatChannel;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onRequest(AtmosphereResource resource) throws IOException {
        String body = resource.getRequest().body().asString();

        if (body != null && !body.isBlank()) {
            handleMessage(resource, body);
        } else {
            // Initial connection — suspend to keep the transport open
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
        // nothing to clean up
    }

    @SuppressWarnings("unchecked")
    private void handleMessage(AtmosphereResource resource, String body) throws IOException {
        Map<String, Object> payload = objectMapper.readValue(body, Map.class);
        String userMessage = (String) payload.get("message");

        if (userMessage == null || userMessage.isBlank()) {
            return;
        }
        userMessage = userMessage.trim();

        // Echo user bubble + show typing indicator to the sender
        AtmosphereResponse response = resource.getResponse();
        response.write(
                oobAppend("chat-messages", userBubble(userMessage)) +
                oobReplace("typing-indicator", typingDots())
        );
        response.flushBuffer();

        // Call agent (blocking — background tasks may push messages via
        // AtmosphereChatChannel's broadcaster during this call)
        String agentResponse = chatChannel.chat(userMessage);

        // Send agent response + clear typing indicator
        response.write(
                oobAppend("chat-messages", agentBubble(agentResponse)) +
                oobReplace("typing-indicator", "")
        );
        response.flushBuffer();
    }

    // ---- HTML helpers (same format as JavaClaw's ChatWebSocketHandler) ----

    static String userBubble(String text) {
        return "<article class=\"ar-msg ar-msg--user\">" +
                "<div class=\"ar-msg__bubble\">" + escape(text) + "</div>" +
                "</article>";
    }

    static String agentBubble(String text) {
        return "<article class=\"ar-msg ar-msg--agent\">" +
                "<div class=\"ar-msg__avatar\">JC</div>" +
                "<div class=\"ar-msg__bubble\">" + escape(text) + "</div>" +
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
