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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;

/**
 * Exposes JavaClaw's full agent capabilities as Atmosphere A2A skills.
 * When Atmosphere is added, external AI tools (Claude Desktop, Cursor, VS Code)
 * and other agents can call JavaClaw's tools via A2A/MCP protocols.
 *
 * <p>Skills exposed:</p>
 * <ul>
 *   <li><b>ask</b> — Ask the JavaClaw agent (uses all configured tools: web search,
 *       file system, shell, tasks, checklists, MCP tools)</li>
 *   <li><b>task</b> — Create or manage a task</li>
 *   <li><b>search</b> — Search the web using JavaClaw's configured search provider</li>
 * </ul>
 *
 * <p>These skills are auto-discovered via Atmosphere's {@code @Agent} headless mode
 * and exposed at the A2A and MCP endpoints.</p>
 */
public class JavaClawToolBridge {

    private static final Logger log = LoggerFactory.getLogger(JavaClawToolBridge.class);

    private final Agent agent;
    private final ChatClient chatClient;

    public JavaClawToolBridge(Agent agent, ChatClient chatClient) {
        this.agent = agent;
        this.chatClient = chatClient;
    }

    /**
     * Full agent interaction — uses ALL JavaClaw tools (web search, file system,
     * shell, tasks, checklists, MCP tools). The agent reasons about which tools
     * to call based on the message.
     */
    @AgentSkill(id = "ask", name = "Ask JavaClaw",
            description = "Ask the JavaClaw AI agent a question. The agent has access to web search, "
                    + "file system, shell commands, task management, and all configured MCP tools. "
                    + "It will reason about which tools to use and return a comprehensive answer.",
            tags = {"agent", "chat", "tools", "javaclaw"})
    @AgentSkillHandler
    public void ask(TaskContext task,
                    @AgentSkillParam(name = "message", description = "The question or request") String message) {
        task.updateStatus(TaskState.WORKING, "Processing: " + message);
        try {
            var response = agent.respondTo(message);
            task.addArtifact(Artifact.text(response));
            task.complete("Response generated");
        } catch (Exception e) {
            log.error("Agent call failed", e);
            task.fail("Agent error: " + e.getMessage());
        }
    }

    /**
     * Web search — delegates to the agent with a search-focused prompt.
     * Uses whatever search provider JavaClaw has configured (Brave, Google, etc.).
     */
    @AgentSkill(id = "search", name = "Web Search",
            description = "Search the web using JavaClaw's configured search provider. "
                    + "Returns summarized results from web pages.",
            tags = {"search", "web", "browse"})
    @AgentSkillHandler
    public void search(TaskContext task,
                       @AgentSkillParam(name = "query", description = "Search query") String query) {
        task.updateStatus(TaskState.WORKING, "Searching: " + query);
        try {
            var response = agent.respondTo("Search the web for: " + query
                    + ". Return a concise summary of the top results.");
            task.addArtifact(Artifact.text(response));
            task.complete("Search complete");
        } catch (Exception e) {
            task.fail("Search failed: " + e.getMessage());
        }
    }

    /**
     * Task management — create, list, or update tasks via JavaClaw's TaskTool.
     */
    @AgentSkill(id = "task", name = "Manage Tasks",
            description = "Create, list, or update tasks. JavaClaw persists tasks as markdown "
                    + "files in the workspace directory.",
            tags = {"task", "productivity", "management"})
    @AgentSkillHandler
    public void manageTask(TaskContext task,
                           @AgentSkillParam(name = "action", description = "What to do: 'create', 'list', 'complete', or describe the task") String action) {
        task.updateStatus(TaskState.WORKING, "Managing task: " + action);
        try {
            var response = agent.respondTo(action);
            task.addArtifact(Artifact.text(response));
            task.complete("Task action complete");
        } catch (Exception e) {
            task.fail("Task management failed: " + e.getMessage());
        }
    }

    /**
     * File operations — read, write, or list files in the workspace.
     */
    @AgentSkill(id = "files", name = "File Operations",
            description = "Read, write, edit, or list files in the JavaClaw workspace. "
                    + "The agent has access to the file system tools.",
            tags = {"files", "filesystem", "workspace"})
    @AgentSkillHandler
    public void fileOps(TaskContext task,
                        @AgentSkillParam(name = "request", description = "What to do with files") String request) {
        task.updateStatus(TaskState.WORKING, "File operation: " + request);
        try {
            var response = agent.respondTo(request);
            task.addArtifact(Artifact.text(response));
            task.complete("File operation complete");
        } catch (Exception e) {
            task.fail("File operation failed: " + e.getMessage());
        }
    }
}
