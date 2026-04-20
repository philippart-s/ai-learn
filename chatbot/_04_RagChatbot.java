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

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.MetadataStorageConfig;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;

import java.sql.Connection;
import java.sql.ResultSet;

// Assistant interface with a method for chatting.
// java-18
interface RagAssistant {
  @SystemMessage("""
      Tu es un expert de la conférence Devoxx France.
      Réponds de manière structurée et concise en te basant
      uniquement sur les informations qui te sont fournies.
      Si tu ne trouves pas l'information dans le contexte fourni,
      indique-le clairement.
      """)
  TokenStream chat(String userMessage);
}

/// RAG chatbot: embeds Devoxx talks into a vector store, then retrieves
/// only the relevant talks for each question instead of injecting the
/// full program into every request.
void main() {

  // LLM model configuration
  // java-19
  var chatModel = OpenAiStreamingChatModel.builder()
      .apiKey(System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN"))
      .modelName(System.getenv("OVH_AI_ENDPOINTS_MODEL_NAME"))
      .baseUrl(System.getenv("OVH_AI_ENDPOINTS_MODEL_URL"))
      .reasoningEffort("low")
      .temperature(0.0)
      .logRequests(false)
      .logResponses(false)
      .build();

  // Embedding model configuration
  // java-20
  EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
      .apiKey(System.getenv("OVH_AI_ENDPOINTS_EMBEDDING_MODEL_ACCESS_TOKEN"))
      .modelName(System.getenv("OVH_AI_ENDPOINTS_EMBEDDING_MODEL_NAME"))
      .baseUrl(System.getenv("OVH_AI_ENDPOINTS_EMBEDDING_MODEL_URL"))
      .logRequests(false)
      .logResponses(false)
      .build();

  // --- Version 1: In-memory vector store (re-embeds every run) ---
  // java-21
  EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

  // --- Version 2: PostgreSQL pgvector (persistent, skips re-indexation) ---
  // java-27
//  EmbeddingStore<TextSegment> embeddingStore = PgVectorEmbeddingStore.builder()
//      .host(System.getenv("PGVECTOR_HOST"))
//      .port(Integer.parseInt(System.getenv("PGVECTOR_PORT")))
//      .database(System.getenv("PGVECTOR_DATABASE"))
//      .user(System.getenv("PGVECTOR_USER"))
//      .password(System.getenv("PGVECTOR_PASSWORD"))
//      .table("devoxx_embeddings")
//      .dimension(embeddingModel.dimension())
//      .createTable(true)
//      .dropTableFirst(true)
//      .build();

  // Load Devoxx talks and create text segments for the vector store
  // java-22
  var talks = DevoxxUtils.loadDevoxxTalks();
  IO.println("📋 %d talks chargés depuis le programme Devoxx".formatted(talks.size()));

  var segments = DevoxxUtils.createTextSegments(talks);
  IO.println("🔪 %d segments créés (1 segment par talk)".formatted(segments.size()));

  // Embed the segments and store the embeddings in the vector store
  // java-23
  int batchSize = 25;
  int totalBatches = Math.ceilDiv(segments.size(), batchSize);
  IO.println("🧮 Embedding des segments en cours (%d batches de %d max)...".formatted(totalBatches, batchSize));

  int totalEmbedded = 0;
  for (var i = 0; i < segments.size(); i += batchSize) {
    var batch = segments.subList(i, Math.min(i + batchSize, segments.size()));
    var embeddings = embeddingModel.embedAll(batch)
        .content();
    embeddingStore.addAll(embeddings, batch);
    totalEmbedded += embeddings.size();
    IO.println("  📦 Batch %d/%d — %d segments embeddés".formatted((i / batchSize) + 1, totalBatches, totalEmbedded));
  }
  IO.println("✅ %d embeddings stockés dans le vector store%n".formatted(totalEmbedded));

  // Create the content retriever that will fetch relevant talks based on the question
  // java-24
  var contentRetriever = EmbeddingStoreContentRetriever.builder()
      .embeddingStore(embeddingStore)
      .embeddingModel(embeddingModel)
      .maxResults(30)
      .minScore(0.1)
      .build();

  // Create the assistant
  // java-25
  var assistant = AiServices.builder(RagAssistant.class)
      .streamingChatModel(chatModel)
      .contentRetriever(contentRetriever)
      .build();

  // Ask question to the assistant. -> assistant.chat(prompt)
  // java-26
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
