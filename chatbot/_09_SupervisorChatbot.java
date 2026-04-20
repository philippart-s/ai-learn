/// usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS dev.langchain4j:langchain4j:1.12.2
//DEPS dev.langchain4j:langchain4j-open-ai:1.12.2
//DEPS dev.langchain4j:langchain4j-agentic:1.12.2-beta22
//DEPS dev.langchain4j:langchain4j-pgvector:1.12.2-beta22
//DEPS org.postgresql:postgresql:42.7.5
//DEPS com.google.code.gson:gson:2.10.1
//DEPS ch.qos.logback:logback-classic:1.5.6
//DEPS redis.clients:jedis:6.0.0
//SOURCES ChatbotUtils.java
//SOURCES DevoxxUtils.java
//SOURCES DevoxxTools.java
//SOURCES FileChatMemoryStore.java
//SOURCES ValkeyChatMemoryStore.java

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;


// Agent 1: TalkSearcher — searches Devoxx talks using RAG + tools
// java-61
public interface TalkSearcher {
  @SystemMessage("""
      Tu es un expert de la conférence Devoxx France 2026.
      Ta mission est de rechercher les talks qui correspondent
      à la demande de l'utilisateur.

      Tu disposes d'outils pour connaître la date et l'heure actuelles.
      Utilise-les quand l'utilisateur mentionne "aujourd'hui", "demain",
      "dans 2 heures", etc.

      Retourne une liste structurée des talks trouvés avec :
      - Date et heure de passage
      - Titre du talk
      - Nom du/des speaker(s)
      - Track
      - Format et durée
      - Un bref résumé

      Si tu ne trouves pas de talks correspondants, indique-le clairement.
      """)
  @UserMessage("{{request}}")
  @Agent(description = "Recherche les talks Devoxx France correspondant aux critères de l'utilisateur", outputKey = "talks")
  String searchTalks(@V("request") String request);
}


// Agent 2: QualityScorer — evaluates search results quality
// java-62
public interface QualityScorer {
  @UserMessage("""
      Tu es un évaluateur critique.
      Évalue la qualité et la pertinence des résultats de recherche
      par rapport à la demande initiale de l'utilisateur.

      Demande de l'utilisateur : "{{request}}"

      Résultats de la recherche :
      {{talks}}

      Donne un score entre 0.0 et 1.0 basé sur :
      - La pertinence des talks trouvés par rapport à la demande
      - La complétude de la réponse (nombre suffisant de talks)
      - La diversité des formats et des tracks

      Retourne uniquement le score numérique, rien d'autre.
      """)
  @Agent(description = "Évalue la qualité des résultats de recherche", outputKey = "score")
  double scoreQuality(@V("request") String request, @V("talks") String talks);
}

// Agent 3: AgendaPlanner — builds a structured 3-day agenda
// java-63
public interface AgendaPlanner {
  @SystemMessage("""
      Tu es un expert en planification de conférences.
      À partir des talks trouvés, crée un agenda structuré
      pour les 3 jours de Devoxx France 2026
      (mercredi, jeudi et vendredi).

      Organise l'agenda de manière claire avec :
      - Un en-tête par jour
      - Les talks regroupés par créneau horaire
      - Le titre, le speaker, le format et la durée de chaque talk
      - Des recommandations personnalisées si pertinent

      Si les créneaux horaires ne sont pas disponibles,
      propose un agenda basé sur les formats et les tracks.
      """)
  @UserMessage("""
      Voici les talks trouvés pour créer l'agenda :
      {{talks}}

      Demande originale de l'utilisateur : "{{request}}"
      """)
  @Agent(description = "Crée un agenda structuré sur 3 jours à partir des résultats de recherche", outputKey = "agenda")
  String createAgenda(@V("talks") String talks, @V("request") String request);
}

void main() {

  // === Non-streaming model (supervisor and all sub-agents are synchronous) ===
  // java-64
  ChatModel chatModel = OpenAiChatModel.builder()
      .apiKey(System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN"))
      .modelName(System.getenv("OVH_AI_ENDPOINTS_MODEL_NAME"))
      .baseUrl(System.getenv("OVH_AI_ENDPOINTS_MODEL_URL"))
      .reasoningEffort("low")
      .temperature(0.0)
      .logRequests(false)
      .logResponses(false)
      .timeout(Duration.ofMinutes(5))
      .build();

  // RAG configuration: embedding model, vector store, retriever
  // Embedding model configuration
  // java-65
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
      .table("devoxx_embeddings")
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

  // === Build Agent 1: TalkSearcher (RAG + DevoxxTools) ===
  // java-66
  TalkSearcher talkSearcher = AgenticServices
      .agentBuilder(TalkSearcher.class)
      .chatModel(chatModel)
      .contentRetriever(contentRetriever)
      .tools(new DevoxxTools())
      .outputKey("talks")
      .build();

  // === Build Agent 2: QualityScorer ===
  // java-67
  QualityScorer qualityScorer = AgenticServices
      .agentBuilder(QualityScorer.class)
      .chatModel(chatModel)
      .outputKey("score")
      .build();

  // === Build Agent 3: AgendaPlanner (synchronous — supervisor does not support streaming) ===
  // java-68
  AgendaPlanner agendaPlanner = AgenticServices
      .agentBuilder(AgendaPlanner.class)
      .chatModel(chatModel)
      .outputKey("agenda")
      .build();

  // Supervisor — pure agentic AI orchestration
  // java-69
  SupervisorAgent supervisor = AgenticServices
      .supervisorBuilder()
      .chatModel(chatModel)
      .subAgents(talkSearcher, qualityScorer, agendaPlanner)
      .listener(new AgentListener() {
        @Override
        public void beforeAgentInvocation(AgentRequest request) {
          IO.println("%n🔄 Agent '%s' invoked...".formatted(request.agentName()));
        }

        @Override
        public void afterAgentInvocation(AgentResponse response) {
          if (response.agentName().equals("scoreQuality")) {
            IO.println("📊 Quality score: %s".formatted(response.output()));
          }
        }

        @Override
        public boolean inheritedBySubagents() {
          return true;
        }
      })
      .build();

  // Run the supervisor on a single question (synchronous — no streaming)
  // java-70
  IO.println("💬: %s".formatted(ChatbotUtils.QUESTIONS.MAKE_AGENDA.toString()));
  IO.println("🤖: ");
  IO.println(supervisor.invoke(ChatbotUtils.QUESTIONS.MAKE_AGENDA.toString()));

  IO.println("%n🔢 Total tokens used: %s%n".formatted(ChatbotUtils.totalTokensUsed));
}
