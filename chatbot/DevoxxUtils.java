import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/// Nested records matching the Devoxx CFP public API JSON structure.
record Speaker(String fullName) {
}

record Track(String name) {
}

record SessionType(String name, int duration) {
}

record Room(String name) {
}

/// Scheduling information for a talk: day, room, and time slot.
/// Populated from the `/api/public/schedules/{day}` endpoint.
/// `fromDate` and `toDate` are ISO-8601 date-time strings already converted
/// to the `Europe/Paris` timezone at build time (not UTC).
record ScheduleSlot(String fromDate, String toDate, String timezone, Room room) {
}

/// A talk from the Devoxx France program.
/// `id` is used to cross-reference with the schedule endpoint.
/// `schedule` is null when the talk has not yet been scheduled.
record Talk(long id, String title, String summary, List<Speaker> speakers, Track track, SessionType sessionType,
            ScheduleSlot schedule) {
}

/// Backing store for chat memory.
enum MemoryStore {
  /// In-memory store — lost when the program exits.
  MEMORY,
  /// File-based store — persists to `chat-memory.json`.
  JSON,
  /// Valkey/Redis store — shared external store.
  VALKEY
}

/// Backing store for the vector embeddings.
enum VectorStore {
  /// In-memory store — re-embeds every run.
  MEMORY,
  /// PostgreSQL pgvector — persistent, skips re-indexation.
  PGVECTOR
}

/// Shared utilities for loading and processing the Devoxx France program.
/// Centralizes API fetching, JSON parsing, text segment creation, and
/// the full RAG pipeline (embedding + vector store + content retriever)
/// so that all chatbot scripts can reuse them without duplication.
public class DevoxxUtils {

  static final String DEVOXX_API_URL = "https://devoxxfr2026.cfp.dev/api/public/talks";
  static final String DEVOXX_SCHEDULE_URL = "https://devoxxfr2026.cfp.dev/api/public/schedules";
  static final String DEVOXX_PROGRAM_FILE = "resources/devoxx-2026-program.json";
  static final String DEVOXX_SCHEDULE_FILE = "resources/devoxx-2026-schedule.json";

  /// Fetches the Devoxx France talk list as raw JSON: tries the CFP API
  /// first, saves the response to a local JSON file as a cache, and
  /// falls back to the local file if the API is unreachable.
  static String fetchDevoxxJson() {
    String json;
    if (!Files.exists(Path.of(DEVOXX_PROGRAM_FILE))) {
      try {
        IO.println("🌐 Fetching Devoxx program from API...");
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
            .uri(URI.create(DEVOXX_API_URL))
            .GET()
            .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        json = response.body();

        // Save to local file for next time
        var path = Path.of(DEVOXX_PROGRAM_FILE);
        Files.createDirectories(path.getParent());
        Files.writeString(path, json);
        IO.println("💾 Program saved to %s".formatted(DEVOXX_PROGRAM_FILE));
      } catch (Exception e) {
        IO.println("⚠️  API unreachable (%s), falling back to local file...".formatted(e.getMessage()));
        try {
          json = Files.readString(Path.of(DEVOXX_PROGRAM_FILE));
        } catch (IOException ioe) {
          throw new RuntimeException("No local file either at %s".formatted(DEVOXX_PROGRAM_FILE), ioe);
        }
      }
    } else {
      IO.println("✅  Devoxx program file exists, load data from it.");
      try {
        json = Files.readString(Path.of(DEVOXX_PROGRAM_FILE));
      } catch (IOException ioe) {
        throw new RuntimeException("No local file either at %s".formatted(DEVOXX_PROGRAM_FILE), ioe);
      }
    }
    return json;
  }

  // --- Gson-only DTOs for schedule deserialization ---
  record RawScheduleLink(String href, String title) {
  }

  record RawScheduleLinks(List<RawScheduleLink> links) {
  }

  record RawProposal(long id) {
  }

  record RawRoomSlot(String fromDate, String toDate, String timezone, Room room, RawProposal proposal) {
  }

  /// Fetches scheduling information from the `/api/public/schedules` endpoint.
  /// Returns a map of `talkId → ScheduleSlot` for all published days.
  /// Only HTTP-reachable; returns an empty map on failure (non-blocking).
  static Map<Long, ScheduleSlot> fetchScheduleMap() {
    var scheduleMap = new HashMap<Long, ScheduleSlot>();
    if (!Files.exists(Path.of(DEVOXX_SCHEDULE_FILE))) {
      var gson = new Gson();
      var client = HttpClient.newHttpClient();
      try {
        // Step 1: fetch the schedule index to get available day URLs
        IO.println("📅 Fetching schedule index...");
        var indexRequest = HttpRequest.newBuilder()
            .uri(URI.create(DEVOXX_SCHEDULE_URL))
            .GET()
            .build();
        var indexResponse = client.send(indexRequest, HttpResponse.BodyHandlers.ofString());
        var scheduleLinks = gson.fromJson(indexResponse.body(), RawScheduleLinks.class);

        if (scheduleLinks == null || scheduleLinks.links() == null) {
          IO.println("⚠️  No schedule days available yet.");
          return scheduleMap;
        }

        // Step 2: fetch each day and collect talkId → ScheduleSlot entries
        Type slotListType = new TypeToken<List<RawRoomSlot>>() {
        }.getType();
        for (var link : scheduleLinks.links()) {
          //IO.println("   📆 Fetching %s...".formatted(link.title()));
          var dayRequest = HttpRequest.newBuilder()
              .uri(URI.create(link.href()))
              .GET()
              .build();
          var dayResponse = client.send(dayRequest, HttpResponse.BodyHandlers.ofString());
          List<RawRoomSlot> slots = gson.fromJson(dayResponse.body(), slotListType);
          for (var slot : slots) {
            if (slot.proposal() != null && slot.room() != null) {
              var tz = slot.timezone() != null ? ZoneId.of(slot.timezone()) : ZoneId.of("Europe/Paris");
              var from = ZonedDateTime.parse(slot.fromDate())
                  .withZoneSameInstant(tz);
              var to = ZonedDateTime.parse(slot.toDate())
                  .withZoneSameInstant(tz);
              scheduleMap.put(
                  slot.proposal()
                      .id(),
                  new ScheduleSlot(from.toString(), to.toString(), slot.timezone(), slot.room())
              );
            }
          }
          IO.println("      → %d slots loaded".formatted(slots.size()));
        }
        IO.println("✅ Schedule map built: %d scheduled talks".formatted(scheduleMap.size()));

        // Save to local file for fallback
        var schedulePath = Path.of(DEVOXX_SCHEDULE_FILE);
        Files.createDirectories(schedulePath.getParent());
        Files.writeString(schedulePath, gson.toJson(scheduleMap));
        IO.println("💾 Schedule saved to %s".formatted(DEVOXX_SCHEDULE_FILE));
      } catch (Exception e) {
        IO.println("⚠️  Could not fetch schedule (%s), trying local file...".formatted(e.getMessage()));
        try {
          var cached = Files.readString(Path.of(DEVOXX_SCHEDULE_FILE));
          Type mapType = new TypeToken<Map<Long, ScheduleSlot>>() {
          }.getType();
          scheduleMap = new Gson().fromJson(cached, mapType);
          IO.println("📂 Schedule loaded from %s (%d talks)".formatted(DEVOXX_SCHEDULE_FILE, scheduleMap.size()));
        } catch (IOException ioe) {
          IO.println("⚠️  No local schedule file at %s — talks will have no scheduling info.".formatted(DEVOXX_SCHEDULE_FILE));
        }
      }
    } else {
      IO.println("✅  Schedule file exists, load data from it.");
      try {
        var cached = Files.readString(Path.of(DEVOXX_SCHEDULE_FILE));
        Type mapType = new TypeToken<Map<Long, ScheduleSlot>>() {
        }.getType();
        scheduleMap = new Gson().fromJson(cached, mapType);
        IO.println("📂 Schedule loaded from %s (%d talks)".formatted(DEVOXX_SCHEDULE_FILE, scheduleMap.size()));
      } catch (IOException ioe) {
        IO.println("⚠️  No local schedule file at %s — talks will have no scheduling info.".formatted(DEVOXX_SCHEDULE_FILE));
      }

    }
    return scheduleMap;
  }

  /// Loads the Devoxx France talk list as parsed [Talk] records.
  /// Used by RAG and Tool chatbots that need structured talk data.
  /// Enriches each talk with scheduling information (day, room, time)
  /// fetched from the schedule endpoint when available.
  static List<Talk> loadDevoxxTalks() {
    var json = fetchDevoxxJson();
    Type listType = new TypeToken<List<Talk>>() {
    }.getType();
    List<Talk> talks = new Gson().fromJson(json, listType);

    // Enrich talks with scheduling data from the schedule endpoint
    var scheduleMap = fetchScheduleMap();
    if (!scheduleMap.isEmpty()) {
      talks = talks.stream()
          .map(t -> new Talk(t.id(), t.title(), t.summary(), t.speakers(), t.track(), t.sessionType(),
              scheduleMap.get(t.id())))
          .toList();
      var scheduled = talks.stream()
          .filter(t -> t.schedule() != null)
          .count();
      IO.println("📌 %d/%d talks enriched with scheduling info".formatted(scheduled, talks.size()));
    }
//    IO.println(talks);
    return talks;
  }

  /// Loads the Devoxx France program as a human-readable formatted string.
  /// Used by the Memory chatbot to inject the full program into chat history.
  static String loadDevoxxProgram() {
    return formatProgram(loadDevoxxTalks());
  }

  /// Formats a [ScheduleSlot] as a human-readable French string.
  /// `fromDate`/`toDate` are already in `Europe/Paris` timezone.
  /// Returns `"Non planifié"` if the slot is null.
  static String formatScheduleSlot(ScheduleSlot slot) {
    if (slot == null) return "Non planifié";
    var from = ZonedDateTime.parse(slot.fromDate());
    var to = ZonedDateTime.parse(slot.toDate());
    var dayFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH);
    var timeFmt = DateTimeFormatter.ofPattern("HH'h'mm", Locale.FRENCH);
    var room = slot.room() != null ? slot.room()
                                     .name() : "Salle inconnue";
    return "%s à %s-%s — %s".formatted(
        from.format(dayFmt), from.format(timeFmt), to.format(timeFmt), room);
  }

  /// Formats the talk list as a human-readable text summary.
  /// Talks with a schedule are sorted by date/time; unscheduled talks follow, sorted by track.
  static String formatProgram(List<Talk> talks) {
    var sb = new StringBuilder();
    sb.append("Programme de Devoxx France 2026 — %d talks :\n\n".formatted(talks.size()));

    var scheduled = talks.stream()
        .filter(t -> t.schedule() != null)
        .sorted((a, b) -> a.schedule()
            .fromDate()
            .compareTo(b.schedule()
                .fromDate()))
        .toList();
    var unscheduled = talks.stream()
        .filter(t -> t.schedule() == null)
        .sorted((a, b) -> a.track()
            .name()
            .compareTo(b.track()
                .name()))
        .toList();

    if (!scheduled.isEmpty()) {
      sb.append("=== Talks planifiés ===\n");
      for (var talk : scheduled) {
        var speakerNames = talk.speakers()
            .stream()
            .map(Speaker::fullName)
            .toList();
        sb.append("- [%s, %d min] %s — %s\n  📅 %s\n".formatted(
            talk.sessionType()
                .name(),
            talk.sessionType()
                .duration(),
            talk.title(),
            String.join(", ", speakerNames),
            formatScheduleSlot(talk.schedule())
        ));
      }
    }

    if (!unscheduled.isEmpty()) {
      sb.append("\n=== Talks non encore planifiés ===\n");
      var currentTrack = "";
      for (var talk : unscheduled) {
        if (!talk.track()
            .name()
            .equals(currentTrack)) {
          currentTrack = talk.track()
              .name();
          sb.append("\n--- %s ---\n".formatted(currentTrack));
        }
        var speakerNames = talk.speakers()
            .stream()
            .map(Speaker::fullName)
            .toList();
        sb.append("- [%s, %d min] %s — %s\n".formatted(
            talk.sessionType()
                .name(),
            talk.sessionType()
                .duration(),
            talk.title(),
            String.join(", ", speakerNames)
        ));
      }
    }

    return sb.toString();
  }

  /// Parses the JSON talk list and formats it as a human-readable text summary.
  /// Used when only raw JSON is available (e.g. from local cache without schedule enrichment).
  static String formatProgram(String json) {
    Type listType = new TypeToken<List<Talk>>() {
    }.getType();
    List<Talk> talks = new Gson().fromJson(json, listType);
    return formatProgram(talks);
  }

  /// Converts each talk into a [TextSegment] with structured text content
  /// and metadata fields (track, session type, speakers, duration).
  /// Each talk becomes one segment — no further splitting is needed
  /// because individual talks are short enough to fit in a single chunk.
  /// Speaker names appear both at the top and inline so the embedding
  /// model gives them enough weight for name-based searches.
  /// When scheduling info is available, day/room/time are included both
  /// in the text body (for semantic search) and in metadata (for filtering).
  static List<TextSegment> createTextSegments(List<Talk> talks) {
    return talks.stream()
        .map(talk -> {
          var speakerNames = talk.speakers()
              .stream()
              .map(Speaker::fullName)
              .toList();

          var speakers = String.join(", ", speakerNames);
          var scheduleInfo = formatScheduleSlot(talk.schedule());
          var text = """
              Talk de %s
              Titre: %s
              Track: %s
              Format: %s (%d min)
              Speakers: %s
              Planification: %s
              Résumé: %s
              """.formatted(
              speakers,
              talk.title(),
              talk.track()
                  .name(),
              talk.sessionType()
                  .name(),
              talk.sessionType()
                  .duration(),
              speakers,
              scheduleInfo,
              talk.summary() != null ? talk.summary() : "Pas de résumé disponible"
          );

          var metadata = dev.langchain4j.data.document.Metadata.from("track", talk.track()
                  .name())
              .put("sessionType", talk.sessionType()
                  .name())
              .put("duration", talk.sessionType()
                  .duration())
              .put("speakers", String.join(", ", speakerNames))
              .put("title", talk.title())
              .put("schedule", scheduleInfo);

          if (talk.schedule() != null) {
            var slot = talk.schedule();
            var from = ZonedDateTime.parse(slot.fromDate());
            metadata
                .put("day", from.format(DateTimeFormatter.ofPattern("EEEE", Locale.FRENCH)))
                .put("room", slot.room() != null ? slot.room()
                                                   .name() : "")
                .put("fromDate", slot.fromDate())
                .put("toDate", slot.toDate());
          }

          return TextSegment.from(text, metadata);
        })
        .toList();
  }

  /// Creates a [MessageWindowChatMemory] with the specified backing store.
  ///
  /// @param store the type of store to use
  static MessageWindowChatMemory createChatMemory(MemoryStore store) {
    var builder = MessageWindowChatMemory.builder()
        .maxMessages(20);
    return switch (store) {
      case MEMORY -> builder.build();
      case JSON -> builder.chatMemoryStore(new FileChatMemoryStore())
          .build();
      case VALKEY -> builder.chatMemoryStore(new ValkeyChatMemoryStore())
          .build();
    };
  }
}
