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
//SOURCES BearConfTools.java
//SOURCES FileChatMemoryStore.java
//SOURCES ValkeyChatMemoryStore.java

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import java.time.Duration;

// Assistant interface with a method for chatting.
// java-28
interface ToolAssistant {
  @SystemMessage("""
      Tu es un expert de la conférence BearConf.
      Réponds de manière structurée et concise en te basant
      uniquement sur les informations qui te sont fournies.
      Si tu ne trouves pas l'information dans le contexte fourni,
      indique-le clairement.
      
      Tu disposes d'outils pour connaître la date et l'heure actuelles.
      Utilise TOUJOURS les outils à ta disposition pour connaitre des informations sur l'heure et la date.
      Utilise-les quand l'utilisateur mentionne "aujourd'hui", "demain", "dans 2 heures", etc.
      
      Sois concis dans tes réponses.
      """)
  TokenStream chat(String userMessage);
}

void main() {

  // Model configuration
  // java-26
  var chatModel = OpenAiStreamingChatModel.builder()
      .apiKey(System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN"))
      .modelName(System.getenv("OVH_AI_ENDPOINTS_MODEL_NAME"))
      .baseUrl(System.getenv("OVH_AI_ENDPOINTS_MODEL_URL"))
      .timeout(Duration.ofMinutes(5))
      .reasoningEffort("low")
      .temperature(0.0)
      .logRequests(false)
      .logResponses(false)
      .build();

  // RAG configuration: embedding model, vector store, retriever
  // Embedding model configuration
  // java-27
  EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
      .apiKey(System.getenv("OVH_AI_ENDPOINTS_EMBEDDING_MODEL_ACCESS_TOKEN"))
      .modelName(System.getenv("OVH_AI_ENDPOINTS_EMBEDDING_MODEL_NAME"))
      .baseUrl(System.getenv("OVH_AI_ENDPOINTS_EMBEDDING_MODEL_URL"))
      .logRequests(false)
      .logResponses(false)
      .build();

  var embeddingStore = PgVectorEmbeddingStore.builder()
      .host(System.getenv("PGVECTOR_HOST"))
      .port(Integer.parseInt(System.getenv("PGVECTOR_PORT")))
      .database(System.getenv("PGVECTOR_DATABASE"))
      .user(System.getenv("PGVECTOR_USER"))
      .password(System.getenv("PGVECTOR_PASSWORD"))
      .table("bearconf_embeddings")
      .dimension(embeddingModel.dimension())
      .createTable(false)
      .dropTableFirst(false)
      .build();

  var contentRetriever = EmbeddingStoreContentRetriever.builder()
      .embeddingStore(embeddingStore)
      .embeddingModel(embeddingModel)
      .maxResults(30)
      .minScore(0.1)
      .build();

  // Assistant configuration with tools
  // java-30
  var assistant = AiServices.builder(ToolAssistant.class)
      .streamingChatModel(chatModel)
      .contentRetriever(contentRetriever)
      .tools(new BearConfTools())
      .toolArgumentsErrorHandler(ToolArgumentsErrorHandler.sendExceptionMessageToLlm())
      .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocationUnlessVisibleToLlm())
      .build();

  // Ask question to the assistant. -> assistant.chat(prompt)
  // java-31
  ChatbotUtils.runInteractive(assistant::chat,
      ChatbotUtils.QUESTIONS.TALKS_TODAY,
      ChatbotUtils.QUESTIONS.REMAINING_SEATS,
      ChatbotUtils.QUESTIONS.TALKS_TOMORROW);

  IO.println("%n🔢  Total tokens used: %s%n".formatted(ChatbotUtils.totalTokensUsed));
}
