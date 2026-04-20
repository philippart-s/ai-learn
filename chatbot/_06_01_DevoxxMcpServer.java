/// usr/bin/env jbang "$0" "$@" ; exit $?

//JAVA 25

//DEPS dev.langchain4j:langchain4j:1.12.2
//DEPS dev.langchain4j:langchain4j-community-mcp-server:1.12.2-beta22
//DEPS dev.langchain4j:langchain4j-pgvector:1.12.2-beta22
//DEPS org.postgresql:postgresql:42.7.5
//DEPS com.google.code.gson:gson:2.10.1
//DEPS redis.clients:jedis:6.0.0
//DEPS ch.qos.logback:logback-classic:1.5.6
//SOURCES DevoxxUtils.java
//SOURCES FileChatMemoryStore.java
//SOURCES ValkeyChatMemoryStore.java

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.community.mcp.server.McpServer;
import dev.langchain4j.community.mcp.server.transport.StdioMcpServerTransport;
import dev.langchain4j.mcp.protocol.McpImplementation;

import java.util.List;

/// MCP tools that expose the Devoxx France 2026 program.
/// These tools are discovered and called by the MCP client (McpChatbot)
/// via the stdio JSON-RPC protocol.
class DevoxxMcpTools {

  private final List<Talk> talks;

  // Load the Devoxx talks once at startup to avoid repeated loading on each tool call.
  // java-32
  DevoxxMcpTools() {
    // Load talks once at startup
    this.talks = DevoxxUtils.loadDevoxxTalks();
    // see https://docs.langchain4j.dev/tutorials/mcp-stdio-server#start-the-stdio-server for the reason why we log to stderr here
    System.err.println("📚 %d talks chargés depuis le programme Devoxx".formatted(talks.size()));
  }


  // Tool to get the complete list of talks with details.
  // java-33
  @Tool("Retourne la liste complète des talks à Devoxx France 2026 avec le titre, les speakers, la track et le format.")
  String getAllTalks() {
    System.err.println("🔧 Outil MCP appelé : getAllTalks()");

    return "Il y a %d talks à Devoxx France 2026 :\n\n".formatted(talks.size())
                 + talks.stream()
                     .map(this::formatTalk)
                     .collect(Collectors.joining());
  }

  // Tool to get talks filtered by track.
  // java-33
  @Tool("Retourne tous les talks d'une track donnée à Devoxx France 2026. Utilisez cet outil quand l'utilisateur demande les talks d'une track spécifique comme 'Java', 'Cloud', 'AI & ML', etc.")
  String getTalksByTrack(@P("Le nom de la track, par exemple 'Java, JVM', 'Cloud, Containers & Infrastructure', 'AI & ML") String track) {
    System.err.println("🔧 Outil MCP appelé : getTalksByTrack(\"%s\")".formatted(track));
    var filtered = talks.stream()
        .filter(t -> t.track().name().toLowerCase().contains(track.toLowerCase()))
        .toList();

    if (filtered.isEmpty()) {
      return " Aucun talk trouvé dans la track '%s'. Tracks disponibles : %s".formatted(
          track,
          talks.stream().map(t -> t.track().name()).distinct().sorted().toList());
    }

    return "%d talks dans la track '%s' :\n\n".formatted(filtered.size(), track)
           + filtered.stream()
               .map(this::formatTalk)
               .collect(Collectors.joining());
  }

  /// Tool to get talks by session type (format)
  @Tool("""
        Retourne tous les talks d'un type de session à Devoxx France 2026.
        Utilise cet outil quand l'utilisateur demande les talks d'une session spécifique comme :
            - Keynotes
            - Deep Dive
            - Hands-on Lab
            - BOF (Birds of a Feather)
            - Conference
            - Lunch Talk
            - Tools-in-Action
        """)
  String getTalksBySession(@P("Le nom de la session, par exemple 'Keynote', 'Deep Dive', 'Hands-on Lab', ...") String session) {
    System.err.printf("🔧  Outil MCP appelé : getTalksBySession(\"%s\")%n", session);
    var filtered = talks.stream()
        .filter(t -> t.sessionType().name().toLowerCase().contains(session.toLowerCase()))
        .toList();

    if (filtered.isEmpty()) {
      return " Aucune talk trouvé dans la session '%s'. Sessions disponibles : %s".formatted(
          session,
          talks.stream().map(t -> t.sessionType().name()).distinct().sorted().toList());
    }

    return "%d talks dans la session '%s' :\n\n".formatted(filtered.size(), session)
           + filtered.stream()
               .map(this::formatTalk)
               .collect(Collectors.joining());
  }

  // Tool to search talks by keyword in title, summary, or speaker name.
  // java-34
  @Tool("Recherche des talks à Devoxx France 2026 par mot-clé dans le titre, le résumé ou le nom du speaker. Utilisez cet outil pour les requêtes générales sur des sujets ou des speakers.")
  String searchTalks(@P("Le mot-clé à rechercher dans les titres des talks, les résumés et les noms des speakers") String keyword) {
    System.err.println("🔧 Outil MCP appelé : searchTalks(\"%s\")".formatted(keyword));
    var lowerKeyword = keyword.toLowerCase();
    var filtered = talks.stream()
        .filter(t -> t.title().toLowerCase().contains(lowerKeyword)
            || (t.summary() != null && t.summary().toLowerCase().contains(lowerKeyword))
            || t.speakers().stream().anyMatch(s -> s.fullName().toLowerCase().contains(lowerKeyword)))
        .toList();

    if (filtered.isEmpty()) {
      return "Aucun talk trouvé pour le mot-clé '%s'.".formatted(keyword);
    }

    return "%d talks dans la track '%s' :\n\n".formatted(filtered.size(), keyword)
           + filtered.stream()
               .map(this::formatTalk)
               .collect(Collectors.joining());
  }

  private String formatTalk(Talk talk) {
    var speakers = talk.speakers().stream()
        .map(Speaker::fullName)
        .toList();
    return "Jour et heure de passage : %s - [%s, %d min] %s — %s (Track: %s)\n".formatted(
        talk.schedule().fromDate(),
        talk.sessionType().name(),
        talk.sessionType().duration(),
        talk.title(),
        String.join(", ", speakers),
        talk.track().name());
  }
}

/// MCP server that exposes the Devoxx France 2026 program as tools.
/// Communicates via stdio (JSON-RPC over stdin/stdout).
/// Launched as a subprocess by McpChatbot via StdioMcpTransport.
void main() throws InterruptedException {
  // java-35
  System.err.println("🚀 Démarrage du serveur MCP Devoxx ...");

  var serverInfo = new McpImplementation("devoxx-mcp-server", "1.0.0");

  var server = new McpServer(
      List.of(new DevoxxMcpTools()),
      serverInfo
  );

  new StdioMcpServerTransport(System.in, System.out, server);

  System.err.println("✅  Serveur MCP Devoxx prêt — en attente de requêtes sur stdin");

  // Keep the process alive while stdio is open
  Thread.currentThread().join();
}
