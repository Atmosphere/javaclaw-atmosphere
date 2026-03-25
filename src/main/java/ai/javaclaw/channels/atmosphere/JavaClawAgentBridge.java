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
import org.atmosphere.a2a.annotation.AgentSkill;
import org.atmosphere.a2a.annotation.AgentSkillHandler;
import org.atmosphere.a2a.annotation.AgentSkillParam;
import org.atmosphere.a2a.runtime.TaskContext;
import org.atmosphere.a2a.types.Artifact;
import org.atmosphere.a2a.types.TaskState;
import org.atmosphere.ai.StreamingSession;
import org.atmosphere.ai.annotation.Prompt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;

/**
 * Bridges JavaClaw's {@link Agent} into Atmosphere's {@code @Agent} ecosystem.
 *
 * <p>This class is NOT annotated with {@code @Agent} directly — it's registered
 * programmatically by {@link AtmosphereChannelAutoConfiguration} so it can receive
 * the Spring-managed {@code Agent} and {@code ChatClient} beans.</p>
 *
 * <p>Exposes:</p>
 * <ul>
 *   <li>{@code @Prompt} — streaming AI chat via WebSocket</li>
 *   <li>AgentSkill chat — A2A-discoverable chat skill</li>
 *   <li>AgentSkill ask — synchronous QA via JavaClaw agent</li>
 * </ul>
 */
public class JavaClawAgentBridge {

    private static final Logger log = LoggerFactory.getLogger(JavaClawAgentBridge.class);

    private final Agent agent;
    private final ChatClient chatClient;

    public JavaClawAgentBridge(Agent agent, ChatClient chatClient) {
        this.agent = agent;
        this.chatClient = chatClient;
    }

    /**
     * Streaming AI chat — tokens delivered via WebSocket in real-time.
     * Uses the same ChatClient as JavaClaw (with all advisors, tools, memory).
     */
    @Prompt
    public void onChat(String message, StreamingSession session) {
        log.info("Streaming chat: {}", message);
        try {
            chatClient.prompt(message)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, ChatMemory.CONVERSATION_ID))
                    .stream()
                    .content()
                    .doOnNext(session::send)
                    .doOnComplete(session::complete)
                    .doOnError(session::error)
                    .subscribe();
        } catch (Exception e) {
            log.error("Streaming failed", e);
            session.error(e);
        }
    }

    /**
     * A2A-discoverable skill: ask the JavaClaw agent a question.
     * Returns the full response (non-streaming) via the A2A task protocol.
     */
    @AgentSkill(id = "ask", name = "Ask Agent",
            description = "Ask the JavaClaw agent a question and get a complete response",
            tags = {"chat", "agent", "javaclaw"})
    @AgentSkillHandler
    public void ask(TaskContext task,
                    @AgentSkillParam(name = "message", description = "The question to ask") String message) {
        task.updateStatus(TaskState.WORKING, "Processing: " + message);
        try {
            var response = agent.respondTo(message, "atmosphere");
            task.addArtifact(Artifact.text(response));
            task.complete("Response generated");
        } catch (Exception e) {
            task.fail("Agent error: " + e.getMessage());
        }
    }
}
