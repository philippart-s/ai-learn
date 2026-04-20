/// usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS dev.langchain4j:langchain4j:1.12.2
//DEPS dev.langchain4j:langchain4j-open-ai:1.12.2
//DEPS dev.langchain4j:langchain4j-mcp:1.12.2-beta22
//DEPS dev.langchain4j:langchain4j-pgvector:1.12.2-beta22
//DEPS org.postgresql:postgresql:42.7.5
//DEPS com.google.code.gson:gson:2.10.1
//DEPS ch.qos.logback:logback-classic:1.5.6
//DEPS redis.clients:jedis:6.0.0
//SOURCES ChatbotUtils.java
//SOURCES DevoxxUtils.java
//SOURCES FileChatMemoryStore.java
//SOURCES ValkeyChatMemoryStore.java
//SOURCES DevoxxTools.java


import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.ToolProvider;

// Assistant interface with a method for chatting.
// java-40
interface McpHttpAssistant {
  @SystemMessage("""
      Tu es un expert de la conférence Devoxx France.
      Réponds de manière structurée et concise en te basant
      uniquement sur les informations qui te sont fournies.
      Si tu ne trouves pas l'information dans le contexte fourni,
      indique-le clairement.

      Tu disposes d'outils MCP qui te permettent d'interroger
      le programme de Devoxx France 2026. Utilise-les pour
      répondre aux questions sur les talks, speakers et tracks.
      """)
  @UserMessage("{{userMessage}}")
  TokenStream chat(String userMessage);
}

void main() throws Exception {

  // 1. Create the MCP transport — connects to the running Quarkus MCP server
  // java-41
  McpTransport transport = StreamableHttpMcpTransport.builder()
      .url("http://localhost:8080/mcp")
      .logRequests(false)
      .logResponses(false)
      .build();

  // 2. Create the MCP client
  // java-45
  McpClient mcpClient = new DefaultMcpClient.Builder()
      .transport(transport)
      .build();

  try {
    // 3. Create the tool provider — discovers tools from the MCP server
    // java-46
    ToolProvider toolProvider = McpToolProvider.builder()
        .mcpClients(mcpClient)
        .build();

    // 4. Create the streaming chat model
    // java-47
    var chatModel = OpenAiStreamingChatModel.builder()
        .apiKey(System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN"))
        .modelName(System.getenv("OVH_AI_ENDPOINTS_MODEL_NAME"))
        .baseUrl(System.getenv("OVH_AI_ENDPOINTS_MODEL_URL"))
        .reasoningEffort("low")
        .temperature(0.0)
        .logRequests(false)
        .logResponses(false)
        .build();

    // 5. Build the AI Service with MCP tool provider
    // java-48
    var assistant = AiServices.builder(McpHttpAssistant.class)
        .streamingChatModel(chatModel)
        .toolProvider(toolProvider)
        .tools(new DevoxxTools())
        .build();

    // 6. Run the interactive question menu -> assistant.chat(prompt)
    // java-49
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

  } finally {
    // 8. Clean up — close the MCP client
    mcpClient.close();
  }
}
