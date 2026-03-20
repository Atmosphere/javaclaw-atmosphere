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
import org.atmosphere.cpr.AtmosphereFramework;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

/**
 * Spring Boot auto-configuration that wires up Atmosphere as the chat
 * transport for JavaClaw with streaming AI responses.
 *
 * <p>Uses the same {@link ChatClient} bean as JavaClaw's {@code DefaultAgent}
 * (with all advisors, tools, and memory) but calls {@code .stream()} instead
 * of {@code .call()}, delivering each token to the browser as it arrives.
 *
 * <p>Activated automatically when both {@code AtmosphereFramework} and
 * {@code Agent} are on the classpath.
 */
@AutoConfiguration
@ConditionalOnClass(AtmosphereFramework.class)
@ConditionalOnBean(Agent.class)
public class AtmosphereChannelAutoConfiguration {

    @Bean
    public AtmosphereChatChannel atmosphereChatChannel(Agent agent,
                                                       ChannelRegistry channelRegistry,
                                                       AtmosphereFramework framework) {
        return new AtmosphereChatChannel(agent, channelRegistry, framework);
    }

    @Bean
    public AtmosphereChatHandler atmosphereChatHandler(ChatClient chatClient,
                                                       ChannelRegistry channelRegistry,
                                                       ObjectMapper objectMapper,
                                                       AtmosphereFramework framework) {
        var handler = new AtmosphereChatHandler(chatClient, channelRegistry, objectMapper);
        framework.addAtmosphereHandler(AtmosphereChatChannel.BROADCASTER_PATH, handler);
        return handler;
    }
}
