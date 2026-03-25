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

import ai.javaclaw.agent.Agent;
import ai.javaclaw.channels.Channel;
import ai.javaclaw.channels.ChannelMessageReceivedEvent;
import ai.javaclaw.channels.ChannelRegistry;
import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.BroadcasterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.HtmlUtils;

/**
 * Atmosphere-backed chat channel for JavaClaw. Replaces the default
 * Spring WebSocket {@code ChatChannel} with Atmosphere's {@link Broadcaster}
 * for multi-client support, automatic transport fallback, and reconnection.
 */
public class AtmosphereChatChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(AtmosphereChatChannel.class);

    static final String BROADCASTER_PATH = "/atmosphere/chat";
    static final String CHANNEL_NAME = "Web Chat Channel";

    private final Agent agent;
    private final ChannelRegistry channelRegistry;
    private final org.springframework.context.ApplicationContext ctx;
    private volatile AtmosphereFramework framework;

    public AtmosphereChatChannel(Agent agent, ChannelRegistry channelRegistry,
                                 org.springframework.context.ApplicationContext ctx) {
        this.agent = agent;
        this.channelRegistry = channelRegistry;
        this.ctx = ctx;
        channelRegistry.registerChannel(this);
        log.info("Started Atmosphere Chat channel");
    }

    private AtmosphereFramework framework() {
        if (framework == null) {
            framework = ctx.getBean(AtmosphereFramework.class);
        }
        return framework;
    }

    @Override
    public String getName() {
        return CHANNEL_NAME;
    }

    /**
     * Handles a chat message from the web UI. Publishes a channel event
     * so background tasks route responses here, then delegates to the agent.
     */
    public String chat(String message) {
        channelRegistry.publishMessageReceivedEvent(
                new ChannelMessageReceivedEvent(getName(), message));
        return agent.respondTo(message, "atmosphere");
    }

    /**
     * Delivers a background-task message to all connected clients via
     * Atmosphere's {@link Broadcaster}. Unlike the default ChatChannel,
     * this supports multiple simultaneous browser sessions.
     */
    @Override
    public void sendMessage(String message) {
        BroadcasterFactory factory = framework().getBroadcasterFactory();
        if (factory == null) {
            log.warn("Atmosphere framework not yet initialized, message dropped");
            return;
        }
        factory.findBroadcaster(BROADCASTER_PATH)
                .ifPresentOrElse(
                        b -> b.broadcast(buildBackgroundMessageHtml(message)),
                        () -> log.warn("No Atmosphere broadcaster for path '{}', message dropped",
                                BROADCASTER_PATH));
    }

    private static String buildBackgroundMessageHtml(String text) {
        return "<div id=\"chat-messages\" hx-swap-oob=\"beforeend\">" +
                "<article class=\"ar-msg ar-msg--agent\">" +
                "<div class=\"ar-msg__avatar\">JC</div>" +
                "<div class=\"ar-msg__bubble\">" + HtmlUtils.htmlEscape(text) + "</div>" +
                "</article></div>";
    }
}
