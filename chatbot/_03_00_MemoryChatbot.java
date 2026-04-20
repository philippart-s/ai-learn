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

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;

// Assistant interface with a method for chatting.
// java-09
interface MemoryAssistant {
  @SystemMessage(ChatbotUtils.SYSTEM_PROMPT_DEVOXX_FRANCE_EXPERT)
  TokenStream chat(String userMessage);
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
  var chatMemory = MessageWindowChatMemory.builder()
      .maxMessages(20)
      .build();

  // --- Version 2: Persistent chat memory (survives between sessions) ---
  // java-15
//  var chatMemory = MessageWindowChatMemory.builder()
//      .maxMessages(20)
//      .chatMemoryStore(new FileChatMemoryStore())
//      .build();

  // --- Version 3: Valkey/Redis chat memory (shared external store) ---
  // java-17
//  var chatMemory = MessageWindowChatMemory.builder()
//      .maxMessages(20)
//      .chatMemoryStore(new ValkeyChatMemoryStore())
//      .build();

  // Assistant creation with chat memory
  // java-12
  var assistant = AiServices.builder(MemoryAssistant.class)
      .streamingChatModel(model)
      .chatMemory(chatMemory)
      .build();

  // Step 1: Inject the Devoxx 2026 program into the conversation
  // java-13
  var program = DevoxxUtils.loadDevoxxProgram();

  var injectionMessage = """
      Voici le programme de Devoxx France 2026. Mémorise-le pour répondre à mes prochaines questions.

      %s
      """.formatted(program);

  IO.println("📋 Injection du programme Devoxx France 2026 dans la conversation...\n");
  ChatbotUtils.displayChatbotResponse(assistant.chat(injectionMessage));

  // Step 2: Ask follow-up questions that rely on memory of the injected program -> assistant.chat(prompt)
  // java-14
  ChatbotUtils.runInteractive(assistant::chat,
      ChatbotUtils.QUESTIONS.WHERE_IS_DEVOXX,
      ChatbotUtils.QUESTIONS.HOW_MANY_TALKS,
      ChatbotUtils.QUESTIONS.AI_ADD_KNOWLEDGE,
      ChatbotUtils.QUESTIONS.WHEN_IS_DEVOXX,
      ChatbotUtils.QUESTIONS.MAKE_AGENDA,
      ChatbotUtils.QUESTIONS.TALKS_ON_AI,
      ChatbotUtils.QUESTIONS.WEDNESDAY_AGENDA,
      ChatbotUtils.QUESTIONS.SPEAKER_TALKS,
      ChatbotUtils.QUESTIONS.TALKS_TODAY,
      ChatbotUtils.QUESTIONS.TALKS_TOMORROW,
      ChatbotUtils.QUESTIONS.TALKS_AT_1_30_PM);

  IO.println("%n🔢 Total tokens used: %s%n".formatted(ChatbotUtils.totalTokensUsed));
}
