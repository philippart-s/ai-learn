///usr/bin/env jbang "$0" "$@" ; exit $?

//JAVA 25

//DEPS io.quarkus.platform:quarkus-bom:3.32.1@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-http:1.10.2
//DEPS dev.langchain4j:langchain4j:1.12.2
//DEPS dev.langchain4j:langchain4j-pgvector:1.12.2-beta22
//DEPS org.postgresql:postgresql:42.7.5
//DEPS com.google.code.gson:gson:2.10.1
//DEPS redis.clients:jedis:6.0.0
//JAVAC_OPTIONS -parameters
//JAVA_OPTIONS -Djava.util.logging.manager=org.jboss.logmanager.LogManager --add-opens java.base/java.lang=ALL-UNNAMED
//SOURCES DevoxxUtils.java
//SOURCES FileChatMemoryStore.java
//SOURCES ValkeyChatMemoryStore.java
//FILES application.properties=resources/mcp-server-application.properties

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.util.List;
import java.util.stream.Collectors;

/// Quarkus MCP server that exposes the Devoxx France 2026 program as tools.
/// Communicates via HTTP (Streamable HTTP at /mcp).
/// The client (McpHttpChatbot) connects over HTTP instead of spawning a subprocess.
///
/// This demonstrates an alternative to the stdio-based DevoxxMcpServer:
/// - The server runs as a standalone HTTP service
/// - Tools are auto-discovered by Quarkus at build time via @Tool annotations
/// - The client connects via HttpMcpTransport to http://localhost:8080/mcp
@ApplicationScoped
public class _07_01_DevoxxMcpQuarkusServer {

  private List<Talk> talks;

  void onStart(@Observes StartupEvent event) {
    this.talks = DevoxxUtils.loadDevoxxTalks();
    IO.println("📚 %d talks chargés depuis le programme Devoxx".formatted(talks.size()));
  }

  // Tool to get all talks with details
  // java-42
  @Tool(description = "Retourne la liste complète des talks à Devoxx France 2026 avec le titre, les speakers, la track et le format.")
  String getAllTalks() {
    IO.println("🔧 Outil MCP appelé : getAllTalks()");

    return "Il y a %d talks à Devoxx France 2026 :\n\n".formatted(talks.size())
           + talks.stream()
               .map(this::formatTalk)
               .collect(Collectors.joining());
  }

  // Tool to get talks by track
  // java-43
  @Tool(description = "Retourne tous les talks d'une track donnée à Devoxx France 2026. Utilisez cet outil quand l'utilisateur demande les talks d'une track spécifique comme 'Java', 'Cloud', 'AI & ML', etc.")
  String getTalksByTrack(
      @ToolArg(description = "Le nom de la track, par exemple 'Java, JVM', 'Cloud, Containers & Infrastructure', 'AI & ML") String track) {
    IO.println("🔧 Outil MCP appelé : getTalksByTrack(\"%s\")".formatted(track));
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
  @Tool(description = """
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
  String getTalksBySession(@ToolArg(description = "Le nom de la session, par exemple 'Keynote', 'Deep Dive', 'Hands-on Lab', ...") String session) {
    IO.println("🔧  Outil MCP appelé : getTalksBySession(\"%s\")%n".formatted(session));
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

  // Tool to search talks by keyword in title, summary or speaker name
  // java-44
  @Tool(description = "Recherche des talks à Devoxx France 2026 par mot-clé dans le titre, le résumé ou le nom du speaker. Utilisez cet outil pour les requêtes générales sur des sujets ou des speakers.")
  String searchTalks(
      @ToolArg(description = "Le mot-clé à rechercher dans les titres des talks, les résumés et les noms des speakers") String keyword) {
    IO.println("🔧 Outil MCP appelé : searchTalks(\"%s\")".formatted(keyword));
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

  public static void main(String[] args) {
    Quarkus.run(args);
  }
}
