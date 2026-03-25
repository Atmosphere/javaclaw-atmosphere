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
import ai.javaclaw.channels.ChannelRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

/**
 * Spring Boot auto-configuration that wires up Atmosphere as the chat
 * transport for JavaClaw with streaming AI responses and A2A agent discovery.
 *
 * <p>Registers:</p>
 * <ul>
 *   <li>{@link AtmosphereChatChannel} — JavaClaw Channel for background messages</li>
 *   <li>{@link AtmosphereChatHandler} — WebSocket streaming handler (htmx)</li>
 *   <li>{@link JavaClawAgentBridge} — bridges JavaClaw agent into Atmosphere's
 *       {@code @Agent} ecosystem with A2A discoverable skills</li>
 * </ul>
 *
 * <p>Activated automatically when both {@code AtmosphereFramework} and
 * {@code Agent} are on the classpath.</p>
 */
@AutoConfiguration
@ConditionalOnClass(org.atmosphere.cpr.AtmosphereFramework.class)
@ConditionalOnBean(Agent.class)
public class AtmosphereChannelAutoConfiguration {

    @Bean
    @org.springframework.context.annotation.Primary
    @ConditionalOnProperty(name = "atmosphere.test.synthetic-llm", havingValue = "true")
    public ChatModel syntheticChatModel() {
        return new SyntheticChatModel();
    }

    @Bean
    public AtmosphereChatChannel atmosphereChatChannel(Agent agent,
                                                       ChannelRegistry channelRegistry,
                                                       org.springframework.context.ApplicationContext ctx) {
        // Lazy lookup: AtmosphereFramework bean is created during servlet init
        return new AtmosphereChatChannel(agent, channelRegistry, ctx);
    }

    @Bean
    public AtmosphereChatHandler atmosphereChatHandler(ChatClient chatClient,
                                                       ChannelRegistry channelRegistry,
                                                       ObjectMapper objectMapper,
                                                       org.springframework.context.ApplicationContext ctx) {
        return new AtmosphereChatHandler(chatClient, channelRegistry, objectMapper, ctx);
    }

    /**
     * Registers the chat handler with Atmosphere after the web server starts.
     * Uses ApplicationReadyEvent
     * to ensure the servlet container (and Atmosphere) is fully initialized.
     */
    @Bean
    public org.springframework.context.ApplicationListener<org.springframework.boot.context.event.ApplicationReadyEvent>
    atmosphereHandlerRegistrar(AtmosphereChatHandler handler) {
        return event -> handler.ensureRegistered();
    }

    /**
     * Bridge JavaClaw's agent into Atmosphere's agent ecosystem.
     * Exposes the agent via A2A protocol and MCP for external tool discovery.
     */
    @Bean
    public JavaClawAgentBridge javaClawAgentBridge(Agent agent, ChatClient chatClient) {
        return new JavaClawAgentBridge(agent, chatClient);
    }
}
