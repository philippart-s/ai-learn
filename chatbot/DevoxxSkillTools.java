import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/// Skill-scoped tools for the Devoxx France 2026 companion skill.
/// These tools are only exposed to the LLM after the `devoxx-companion`
/// skill has been activated via the `activate_skill` tool.
/// They provide structured search and filtering over the Devoxx program.
// java-70
public class DevoxxSkillTools {

  private final List<Talk> talks;

  /// Creates the skill tools and loads the Devoxx talk list once at construction time.
  DevoxxSkillTools() {
    this.talks = DevoxxUtils.loadDevoxxTalks();
    IO.println("🎯 DevoxxSkillTools initialized with %d talks".formatted(talks.size()));
  }

  /// Searches talks by keyword in title, summary, or speaker name.
  // java-71
  @Tool("Recherche des talks a Devoxx France 2026 par mot-cle dans le titre, le resume ou le nom du speaker.")
  String searchTalksByKeyword(
      @P("Le mot-cle a rechercher") String keyword) {
    IO.println("%n🔧 Skill tool called: searchTalksByKeyword(\"%s\")".formatted(keyword));
    var lowerKeyword = keyword.toLowerCase();
    var filtered = talks.stream()
        .filter(t -> t.title().toLowerCase().contains(lowerKeyword)
            || (t.summary() != null && t.summary().toLowerCase().contains(lowerKeyword))
            || t.speakers().stream().anyMatch(s -> s.fullName().toLowerCase().contains(lowerKeyword)))
        .toList();

    if (filtered.isEmpty()) {
      return "Aucun talk trouve pour le mot-cle '%s'.".formatted(keyword);
    }

    return "%d talks trouves pour '%s' :\n\n%s".formatted(
        filtered.size(),
        keyword,
        filtered.stream().map(this::formatTalk).collect(Collectors.joining()));
  }

  /// Returns all talks for a given track.
  // java-72
  @Tool("Retourne tous les talks d'une track donnee a Devoxx France 2026.")
  String getTalksByTrack(
      @P("Le nom de la track, par exemple 'Java, JVM', 'Cloud, Containers & Infrastructure', 'AI & ML'") String track) {
    IO.println("%n🔧 Skill tool called: getTalksByTrack(\"%s\")".formatted(track));
    var filtered = talks.stream()
        .filter(t -> t.track().name().toLowerCase().contains(track.toLowerCase()))
        .toList();

    if (filtered.isEmpty()) {
      return "Aucun talk trouve dans la track '%s'. Tracks disponibles : %s".formatted(
          track,
          talks.stream().map(t -> t.track().name()).distinct().sorted().toList());
    }

    return "%d talks dans la track '%s' :\n\n%s".formatted(
        filtered.size(),
        track,
        filtered.stream().map(this::formatTalk).collect(Collectors.joining()));
  }

  /// Returns talks scheduled on a given day (mercredi, jeudi, vendredi).
  // java-73
  @Tool("Retourne les talks programmes un jour donne a Devoxx France 2026. Les jours valides sont : mercredi, jeudi, vendredi.")
  String getTalksByDay(
      @P("Le jour de la semaine en francais : 'mercredi', 'jeudi', ou 'vendredi'") String day) {
    IO.println("%n🔧 Skill tool called: getTalksByDay(\"%s\")".formatted(day));
    var lowerDay = day.toLowerCase();
    var dayFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.FRENCH);

    var filtered = talks.stream()
        .filter(t -> t.schedule() != null)
        .filter(t -> {
          var from = ZonedDateTime.parse(t.schedule().fromDate());
          return from.format(dayFormatter).toLowerCase().equals(lowerDay);
        })
        .sorted((a, b) -> a.schedule().fromDate().compareTo(b.schedule().fromDate()))
        .toList();

    if (filtered.isEmpty()) {
      return "Aucun talk programme le %s. Verifiez que le planning est disponible et que le jour est valide (mercredi, jeudi, vendredi).".formatted(day);
    }

    return "%d talks programmes le %s :\n\n%s".formatted(
        filtered.size(),
        day,
        filtered.stream().map(this::formatTalkWithSchedule).collect(Collectors.joining()));
  }

  /// Returns talks by a specific speaker.
  // java-74
  @Tool("Recherche les talks d'un speaker specifique a Devoxx France 2026.")
  String getTalksBySpeaker(
      @P("Le nom du speaker a rechercher") String speaker) {
    IO.println("%n🔧 Skill tool called: getTalksBySpeaker(\"%s\")".formatted(speaker));
    var lowerSpeaker = speaker.toLowerCase();
    var filtered = talks.stream()
        .filter(t -> t.speakers().stream()
            .anyMatch(s -> s.fullName().toLowerCase().contains(lowerSpeaker)))
        .toList();

    if (filtered.isEmpty()) {
      return "Aucun talk trouve pour le speaker '%s'.".formatted(speaker);
    }

    return "%d talks de '%s' :\n\n%s".formatted(
        filtered.size(),
        speaker,
        filtered.stream().map(this::formatTalk).collect(Collectors.joining()));
  }

  /// Lists all available tracks.
  // java-75
  @Tool("Liste toutes les tracks disponibles a Devoxx France 2026 avec le nombre de talks par track.")
  String getAllTracks() {
    IO.println("%n🔧 Skill tool called: getAllTracks()");
    var trackCounts = talks.stream()
        .collect(Collectors.groupingBy(t -> t.track().name(), Collectors.counting()));

    var sb = new StringBuilder();
    sb.append("Tracks disponibles a Devoxx France 2026 :\n\n");
    trackCounts.entrySet().stream()
        .sorted(java.util.Map.Entry.comparingByKey())
        .forEach(e -> sb.append("- %s (%d talks)\n".formatted(e.getKey(), e.getValue())));
    return sb.toString();
  }

  private String formatTalk(Talk talk) {
    var speakers = talk.speakers().stream()
        .map(Speaker::fullName)
        .toList();
    var scheduleInfo = talk.schedule() != null
        ? " | %s".formatted(DevoxxUtils.formatScheduleSlot(talk.schedule()))
        : "";
    return "- [%s, %d min] %s — %s (Track: %s)%s\n".formatted(
        talk.sessionType().name(),
        talk.sessionType().duration(),
        talk.title(),
        String.join(", ", speakers),
        talk.track().name(),
        scheduleInfo);
  }

  private String formatTalkWithSchedule(Talk talk) {
    var speakers = talk.speakers().stream()
        .map(Speaker::fullName)
        .toList();
    return "- %s | [%s, %d min] %s — %s (Track: %s)\n".formatted(
        DevoxxUtils.formatScheduleSlot(talk.schedule()),
        talk.sessionType().name(),
        talk.sessionType().duration(),
        talk.title(),
        String.join(", ", speakers),
        talk.track().name());
  }
}
