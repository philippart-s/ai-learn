/// usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS dev.langchain4j:langchain4j:1.12.2
//DEPS dev.langchain4j:langchain4j-open-ai:1.12.2
//DEPS dev.langchain4j:langchain4j-web-search-engine-tavily:1.12.2-beta22
//DEPS com.google.code.gson:gson:2.10.1
//DEPS ch.qos.logback:logback-classic:1.5.6
//SOURCES ChatbotUtils.java

import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.web.search.WebSearchTool;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;

// Assistant interface with a method for chatting.

// java-79
interface WebSearchAssistant {
  @SystemMessage("""
      Tu es un expert de la conférence Devoxx France.
      Tu disposes d'un outil de recherche web pour trouver des informations.
      Utilise-le quand tu as besoin de chercher des informations sur Devoxx France
      ou sur tout autre sujet que tu ne connais pas.
      """)
  TokenStream chat(String userMessage);
}

    // java-79
    void main() {
      // Chat model configuration
      // java-80
      var chatModel = OpenAiStreamingChatModel.builder()
          .apiKey(System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN"))
          .modelName(System.getenv("OVH_AI_ENDPOINTS_MODEL_NAME"))
          .baseUrl(System.getenv("OVH_AI_ENDPOINTS_MODEL_URL"))
          .reasoningEffort("low")
          .temperature(0.0)
          .logRequests(false)
          .logResponses(false)
          .build();

      // Tavily web search engine configuration
      // java-92
      var tavilyEngine = TavilyWebSearchEngine.builder()
          .apiKey(System.getenv("TAVILY_API_KEY"))
          .build();

      // Wrap the search engine as a @Tool so the LLM decides when to search
      // java-92
      var webSearchTool = WebSearchTool.from(tavilyEngine);

      // Build the assistant with web search tool
      // java-81
      var assistant = AiServices.builder(WebSearchAssistant.class)
          .streamingChatModel(chatModel)
          .tools(webSearchTool)
          .build();

      // java-82
      IO.println("💬: Résume moi ce qu'est Devoxx France 2026");
      ChatbotUtils.displayChatbotResponse(assistant.chat("Résume moi ce qu'est Devoxx France 2026"));

      IO.println("%n🔢 Total tokens used: %s%n".formatted(ChatbotUtils.totalTokensUsed));
    }
