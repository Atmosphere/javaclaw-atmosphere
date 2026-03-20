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

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

/**
 * Synthetic ChatModel for e2e testing — no external LLM required.
 * <p>
 * Streams a canned markdown response token-by-token, simulating a real
 * LLM with realistic timing. Activated via
 * {@code atmosphere.test.synthetic-llm=true}.
 */
public class SyntheticChatModel implements ChatModel {

    private static final String RESPONSE = """
            Hello! I'm the **JavaClaw** assistant powered by **Atmosphere**.

            Here are some things I can do:

            1. **Stream responses** — word by word, like ChatGPT
            2. **Render markdown** — code, lists, tables
            3. **Multi-client** — multiple browser tabs simultaneously

            ```java
            // Example: Atmosphere streaming
            chatClient.prompt(message)
                .stream()
                .content()
                .doOnNext(token -> resource.write(token));
            ```

            > Powered by Atmosphere Framework — real-time for every Java app.

            What would you like to know?""";

    @Override
    public ChatResponse call(Prompt prompt) {
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(RESPONSE))));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        // Split response into word-sized tokens for realistic streaming
        String[] words = RESPONSE.split("(?<=\\s)");

        return Flux.fromArray(words)
                .delayElements(Duration.ofMillis(30))
                .map(word -> new ChatResponse(
                        List.of(new Generation(new AssistantMessage(word)))));
    }
}
