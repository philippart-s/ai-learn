/// usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS dev.langchain4j:langchain4j:1.12.2
//DEPS dev.langchain4j:langchain4j-open-ai:1.12.2
//DEPS dev.langchain4j:langchain4j-pgvector:1.12.2-beta22
//DEPS org.postgresql:postgresql:42.7.5
//DEPS com.google.code.gson:gson:2.10.1
//DEPS ch.qos.logback:logback-classic:1.5.6
//DEPS redis.clients:jedis:6.0.0
//SOURCES ChatbotUtils.java
//SOURCES DevoxxUtils.java
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

// Assistant interface with a method for chatting.
// java-09
interface MemoryAssistant {
  @SystemMessage(ChatbotUtils.SYSTEM_PROMPT_DEVOXX_FRANCE_EXPERT)
  TokenStream chat(@MemoryId String id, @UserMessage String userMessage);
}

void main() {

  // Model configuration
  // java-10
  var model = OpenAiStreamingChatModel.builder()
      .apiKey(System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN"))
      .modelName(System.getenv("OVH_AI_ENDPOINTS_MODEL_NAME"))
      .baseUrl(System.getenv("OVH_AI_ENDPOINTS_MODEL_URL"))
      .reasoningEffort("low")
      .temperature(0.0)
      .logRequests(false)
      .logResponses(false)
      .build();

  // --- Version 1: In-memory chat memory (lost when the program exits) ---
  // java-11
  ChatMemoryProvider chatMemory = memory -> MessageWindowChatMemory.builder()
      .id(memory)
      .maxMessages(20)
      .build();

  // --- Version 2: Valkey/Redis chat memory (shared external store) ---
//  ChatMemoryProvider chatMemory = memory -> MessageWindowChatMemory.builder()
//      .id(memory)
//      .maxMessages(20)
//      .chatMemoryStore(new ValkeyChatMemoryStore())
//      .build();

  // Assistant creation with chat memory
  // java-12
  var assistant = AiServices.builder(MemoryAssistant.class)
      .streamingChatModel(model)
      .chatMemoryProvider(chatMemory)
      .build();

  // Step 1: Inject the Devoxx 2026 program into the conversation
  // java-13
  var program = DevoxxUtils.loadDevoxxProgram();

  var injectionMessage = """
      Voici le programme de Devoxx France 2026. Mémorise-le pour répondre à mes prochaines questions.

      %s
      """.formatted(program);

  IO.println("📋 Injection du programme Devoxx France 2026 dans la conversation...\n");
  ChatbotUtils.displayChatbotResponse(assistant.chat("stef", injectionMessage));

  // Step 2: Ask follow-up questions that rely on memory of the injected program -> assistant.chat(prompt)
  // java-14
  IO.println("💬 : %s".formatted(ChatbotUtils.QUESTIONS.SPEAKER_TALKS.toString()));
  ChatbotUtils.displayChatbotResponse(assistant.chat("stef", ChatbotUtils.QUESTIONS.SPEAKER_TALKS.toString()));

  IO.readln("Hit a key...");

  IO.println("💬 : %s".formatted(ChatbotUtils.QUESTIONS.SPEAKER_TALKS.toString()));
  ChatbotUtils.displayChatbotResponse(assistant.chat("ny", ChatbotUtils.QUESTIONS.SPEAKER_TALKS.toString()));

  IO.println("%n🔢 Total tokens used: %s%n".formatted(ChatbotUtils.totalTokensUsed));
}
