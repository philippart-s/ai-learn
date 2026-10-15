/// usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS dev.langchain4j:langchain4j:1.22.0
//DEPS dev.langchain4j:langchain4j-open-ai:1.22.0
//DEPS dev.langchain4j:langchain4j-pgvector:1.22.0-beta32
//DEPS org.postgresql:postgresql:42.7.13
//DEPS com.google.code.gson:gson:2.14.0
//DEPS ch.qos.logback:logback-classic:1.6.4
//DEPS redis.clients:jedis:8.0.1
//SOURCES ChatbotUtils.java
//SOURCES BearConfUtils.java
//SOURCES FileChatMemoryStore.java
//SOURCES ValkeyChatMemoryStore.java

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import java.time.Duration;

// Assistant interface with a method for chatting.
// java-09
interface MemoryAssistant {
  @SystemMessage(ChatbotUtils.SYSTEM_PROMPT_BEARCONF_EXPERT)
  TokenStream chat(@MemoryId String id, @UserMessage String userMessage);
}

void main() {

  // Model configuration
  // java-10
  var model = OpenAiStreamingChatModel.builder()
      .apiKey(System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN"))
      .modelName(System.getenv("OVH_AI_ENDPOINTS_MODEL_NAME"))
      .baseUrl(System.getenv("OVH_AI_ENDPOINTS_MODEL_URL"))
      .timeout(Duration.ofMinutes(5))
      .reasoningEffort("low")
      .temperature(0.0)
      .logRequests(false)
      .logResponses(false)
      .build();

  // --- Version 1: In-memory chat memory (lost when the program exits) ---
  // java-11
  ChatMemoryProvider chatMemory = memoryId -> MessageWindowChatMemory.builder()
      .id(memoryId)
      .maxMessages(20)
      .build();

  // --- Version 2: Valkey/Redis chat memory (shared external store) ---
//  ChatMemoryProvider chatMemory = memoryId -> MessageWindowChatMemory.builder()
//      .id(memoryId)
//      .maxMessages(20)
//      .chatMemoryStore(new ValkeyChatMemoryStore())
//      .build();

  // Assistant creation with chat memory
  // java-12
  var assistant = AiServices.builder(MemoryAssistant.class)
      .streamingChatModel(model)
      .chatMemoryProvider(chatMemory)
      .build();

  // Step 1: Inject the BearConf 2026 program into the conversation
  // java-13
  var program = BearConfUtils.loadBearConfProgram();

  var injectionMessage = """
      Voici le programme de BearConf 2026. Mémorise-le pour répondre à mes prochaines questions.

      %s
      """.formatted(program);

  IO.println("📋 Injection du programme BearConf 2026 dans la conversation...\n");
  ChatbotUtils.displayChatbotResponse(assistant.chat("stef", injectionMessage));

  // Step 2: Ask follow-up questions that rely on memory of the injected program -> assistant.chat(prompt)
  // java-14
  IO.println("💬 (Stef) : %s".formatted(ChatbotUtils.QUESTIONS.SPEAKER_TALKS.toString()));
  ChatbotUtils.displayChatbotResponse(assistant.chat("stef", ChatbotUtils.QUESTIONS.SPEAKER_TALKS.toString()));

  IO.readln("Hit a key...");

  IO.println("💬 (Ny) : %s".formatted(ChatbotUtils.QUESTIONS.SPEAKER_TALKS.toString()));
  ChatbotUtils.displayChatbotResponse(assistant.chat("ny", ChatbotUtils.QUESTIONS.SPEAKER_TALKS.toString()));

  IO.println("%n🔢 Total tokens used: %s%n".formatted(ChatbotUtils.totalTokensUsed));
}
