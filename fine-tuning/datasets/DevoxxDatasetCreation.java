/// usr/bin/env jbang "$0" "$@" ; exit $?

//JAVA 25
//PREVIEW

//DEPS com.google.code.gson:gson:2.10.1

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Records to match to the Devoxx France data structure.
record Speaker(String fullName) {}
record Track(String name) {}
record SessionType(String name, int duration) {}
record Talk(String title, String summary, String description,
            List<Speaker> speakers, Track track, SessionType sessionType) {}
// Record for Q&A pairs in the dataset.
record QA(String question, String answer) {}

void main() throws IOException {

  // Load and parse the Devoxx program JSON
  var jsonPath = Path.of("../../chatbot/resources/devoxx-2026-program.json");
  if (!Files.exists(jsonPath)) {
    System.err.println("Fichier programme introuvable : " + jsonPath);
    System.exit(1);
  }

  var json = Files.readString(jsonPath);
  Type listType = new TypeToken<List<Talk>>() {}.getType();
  List<Talk> talks = new Gson().fromJson(json, listType);
  System.out.println("📋 %d talks chargés depuis le programme Devoxx".formatted(talks.size()));

  var dataset = new ArrayList<QA>();

  // ──────────────────────────────────────────────
  // Pre-compute indices used across multiple sections
  // ──────────────────────────────────────────────
  Map<String, List<Talk>> talksBySpeaker = talks.stream()
      .flatMap(talk -> talk.speakers().stream()
          .map(speaker -> Map.entry(speaker.fullName(), talk)))
      .collect(Collectors.groupingBy(
          Map.Entry::getKey,
          Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

  Map<String, List<Talk>> talksByTrack = talks.stream()
      .collect(Collectors.groupingBy(t -> t.track().name()));

  Map<String, List<Talk>> talksByType = talks.stream()
      .collect(Collectors.groupingBy(t -> t.sessionType().name()));

  var tracksSorted = talksByTrack.keySet().stream().sorted().toList();

  // ──────────────────────────────────────────────
  // 1. Per-talk Q&A: atomic questions per talk
  //    Short answers to maximize memorization.
  // ──────────────────────────────────────────────
  int talkQaCount = 0;
  for (var talk : talks) {
    var speakerNames = talk.speakers().stream()
        .map(Speaker::fullName)
        .collect(Collectors.joining(" et "));
    var format = talk.sessionType().name();
    var duration = talk.sessionType().duration();
    var track = talk.track().name();

    // Q: "Who presents talk X?" (atomic: speaker association)
    dataset.add(new QA(
        "Qui présente le talk '%s' à Devoxx France 2026 ?".formatted(talk.title()),
        "Le talk '%s' est présenté par %s.".formatted(talk.title(), speakerNames)
    ));
    talkQaCount++;

    // Q: "What is the format of talk X?" (atomic: format/duration)
    dataset.add(new QA(
        "Quel est le format du talk '%s' ?".formatted(talk.title()),
        "'%s' est un %s de %d minutes dans le track %s.".formatted(
            talk.title(), format, duration, track)
    ));
    talkQaCount++;

    // Q: "What is talk X about?" — only for talks with a summary
    var desc = cleanHtml(talk.summary() != null ? talk.summary() : talk.description());
    if (desc != null && !desc.isBlank()) {
      // Truncate to ~300 chars for shorter answers
      var shortDesc = desc.length() > 300 ? desc.substring(0, 297) + "..." : desc;
      dataset.add(new QA(
          "De quoi parle le talk '%s' à Devoxx France 2026 ?".formatted(talk.title()),
          shortDesc
      ));
      talkQaCount++;
    }
  }
  System.out.println("🎤 %d paires Q&A par talk générées".formatted(talkQaCount));

  // ──────────────────────────────────────────────
  // 2. Speaker-centric Q&A with multiple phrasings
  //    Multi-talk speakers: full listing + variants.
  //    Single-talk speakers: simple confirmation.
  // ──────────────────────────────────────────────
  int speakerQaCount = 0;
  var multiTalkSpeakers = talksBySpeaker.entrySet().stream()
      .filter(e -> e.getValue().size() >= 2)
      .toList();

  System.out.println("👥 %d speakers avec 2+ talks (sur %d total)".formatted(
      multiTalkSpeakers.size(), talksBySpeaker.size()));

  for (var entry : multiTalkSpeakers) {
    var speaker = entry.getKey();
    var speakerTalks = entry.getValue();
    var talkTitles = speakerTalks.stream()
        .map(t -> "- %s (%s, %d min)".formatted(t.title(), t.sessionType().name(), t.sessionType().duration()))
        .collect(Collectors.joining("\n"));

    // Variant 1: "Quels sont les talks de X ?"
    dataset.add(new QA(
        "Quels sont les talks de %s à Devoxx France 2026 ?".formatted(speaker),
        "%s présente %d talks à Devoxx France 2026 :\n%s".formatted(
            speaker, speakerTalks.size(), talkTitles)
    ));
    speakerQaCount++;

    // Variant 2: "Combien de talks présente X ?"
    dataset.add(new QA(
        "Combien de talks présente %s à Devoxx France 2026 ?".formatted(speaker),
        "%s présente %d talks à Devoxx France 2026.".formatted(speaker, speakerTalks.size())
    ));
    speakerQaCount++;

    // Variant 3: "Dans quels tracks intervient X ?"
    var speakerTracks = speakerTalks.stream()
        .map(t -> t.track().name())
        .distinct().sorted().collect(Collectors.joining(", "));
    dataset.add(new QA(
        "Dans quels tracks intervient %s à Devoxx France 2026 ?".formatted(speaker),
        "%s intervient dans les tracks : %s.".formatted(speaker, speakerTracks)
    ));
    speakerQaCount++;

    // Variant 4: "Est-ce que X présente à Devoxx France 2026 ?"
    dataset.add(new QA(
        "Est-ce que %s présente à Devoxx France 2026 ?".formatted(speaker),
        "Oui, %s présente %d talks à Devoxx France 2026.".formatted(speaker, speakerTalks.size())
    ));
    speakerQaCount++;
  }

  // Single-talk speakers: simple confirmation + talk name
  var singleTalkSpeakers = talksBySpeaker.entrySet().stream()
      .filter(e -> e.getValue().size() == 1)
      .toList();

  for (var entry : singleTalkSpeakers) {
    var speaker = entry.getKey();
    var talk = entry.getValue().getFirst();

    // One Q/A per single-talk speaker (confirmation + title)
    dataset.add(new QA(
        "Quel est le talk de %s à Devoxx France 2026 ?".formatted(speaker),
        "%s présente '%s', un %s de %d minutes dans le track %s.".formatted(
            speaker, talk.title(), talk.sessionType().name(),
            talk.sessionType().duration(), talk.track().name())
    ));
    speakerQaCount++;

    // Boolean confirmation variant
    dataset.add(new QA(
        "Est-ce que %s présente à Devoxx France 2026 ?".formatted(speaker),
        "Oui, %s présente le talk '%s' à Devoxx France 2026.".formatted(speaker, talk.title())
    ));
    speakerQaCount++;
  }
  System.out.println("👤 %d paires Q&A speaker générées".formatted(speakerQaCount));

  // ──────────────────────────────────────────────
  // 3. Track-centric Q&A (count + speaker listing)
  // ──────────────────────────────────────────────
  int trackQaCount = 0;
  for (var entry : talksByTrack.entrySet()) {
    var track = entry.getKey();
    var trackTalks = entry.getValue();

    // Count variant
    dataset.add(new QA(
        "Combien de talks y a-t-il dans le track %s ?".formatted(track),
        "Il y a %d talks dans le track %s à Devoxx France 2026.".formatted(
            trackTalks.size(), track)
    ));
    trackQaCount++;

    // Speakers in this track
    var trackSpeakers = trackTalks.stream()
        .flatMap(t -> t.speakers().stream())
        .map(Speaker::fullName)
        .distinct().sorted().collect(Collectors.joining(", "));
    dataset.add(new QA(
        "Quels speakers présentent dans le track %s à Devoxx France 2026 ?".formatted(track),
        "Les speakers du track %s sont : %s.".formatted(track, trackSpeakers)
    ));
    trackQaCount++;

    // Talk titles in this track
    var trackTitles = trackTalks.stream()
        .map(Talk::title)
        .sorted()
        .collect(Collectors.joining(", "));
    dataset.add(new QA(
        "Quels sont les talks du track %s à Devoxx France 2026 ?".formatted(track),
        "Les talks du track %s sont : %s.".formatted(track, trackTitles)
    ));
    trackQaCount++;
  }
  System.out.println("🎯 %d paires Q&A track générées".formatted(trackQaCount));

  // ──────────────────────────────────────────────
  // 4. Session-type Q&A (format-centric)
  // ──────────────────────────────────────────────
  int typeQaCount = 0;
  for (var entry : talksByType.entrySet()) {
    var typeName = entry.getKey();
    var typeTalks = entry.getValue();
    var duration = typeTalks.getFirst().sessionType().duration();

    dataset.add(new QA(
        "Combien de sessions de type %s y a-t-il à Devoxx France 2026 ?".formatted(typeName),
        "Il y a %d sessions de type %s (%d minutes) à Devoxx France 2026.".formatted(
            typeTalks.size(), typeName, duration)
    ));
    typeQaCount++;

    dataset.add(new QA(
        "Combien de temps dure un %s à Devoxx France 2026 ?".formatted(typeName),
        "Un %s dure %d minutes à Devoxx France 2026.".formatted(typeName, duration)
    ));
    typeQaCount++;
  }
  System.out.println("📐 %d paires Q&A format de session générées".formatted(typeQaCount));

  // ──────────────────────────────────────────────
  // 5. Cross-cutting / aggregate Q&A pairs
  // ──────────────────────────────────────────────
  int globalQaCount = 0;

  dataset.add(new QA(
      "Combien de talks y a-t-il à Devoxx France 2026 ?",
      "Il y a %d talks au programme de Devoxx France 2026, répartis dans %d tracks.".formatted(
          talks.size(), talksByTrack.size())
  ));
  globalQaCount++;

  dataset.add(new QA(
      "Quels sont les tracks de Devoxx France 2026 ?",
      "Les tracks de Devoxx France 2026 sont : %s.".formatted(
          String.join(", ", tracksSorted))
  ));
  globalQaCount++;

  dataset.add(new QA(
      "Quels sont les formats de sessions à Devoxx France 2026 ?",
      "Les formats de sessions sont : %s.".formatted(
          talksByType.entrySet().stream()
              .sorted(Map.Entry.comparingByKey())
              .map(e -> "%s (%d min, %d sessions)".formatted(
                  e.getKey(),
                  e.getValue().getFirst().sessionType().duration(),
                  e.getValue().size()))
              .collect(Collectors.joining(", ")))
  ));
  globalQaCount++;

  dataset.add(new QA(
      "C'est quoi Devoxx France ?",
      "Devoxx France est une conférence technique majeure dédiée aux développeurs, qui se tient chaque année à Paris. L'édition 2026 propose %d talks répartis dans %d tracks couvrant des sujets comme %s.".formatted(
          talks.size(), talksByTrack.size(),
          talksByTrack.keySet().stream().sorted().limit(5).collect(Collectors.joining(", ")))
  ));
  globalQaCount++;

  dataset.add(new QA(
      "Quand a lieu Devoxx France 2026 ?",
      "Devoxx France 2026 se tient du 22 au 24 avril 2026 au Palais des Congrès à Paris."
  ));
  globalQaCount++;

  dataset.add(new QA(
      "Où se déroule Devoxx France 2026 ?",
      "Devoxx France 2026 se déroule au Palais des Congrès de Paris."
  ));
  globalQaCount++;

  // Track with most/least talks
  var topTrack = talksByTrack.entrySet().stream()
      .max(Comparator.comparingInt(e -> e.getValue().size()))
      .orElseThrow();
  dataset.add(new QA(
      "Quel est le track avec le plus de talks à Devoxx France 2026 ?",
      "Le track avec le plus de talks est %s avec %d talks.".formatted(
          topTrack.getKey(), topTrack.getValue().size())
  ));
  globalQaCount++;

  var bottomTrack = talksByTrack.entrySet().stream()
      .min(Comparator.comparingInt(e -> e.getValue().size()))
      .orElseThrow();
  dataset.add(new QA(
      "Quel est le track avec le moins de talks à Devoxx France 2026 ?",
      "Le track avec le moins de talks est %s avec %d talks.".formatted(
          bottomTrack.getKey(), bottomTrack.getValue().size())
  ));
  globalQaCount++;

  // Speaker count
  dataset.add(new QA(
      "Combien de speakers y a-t-il à Devoxx France 2026 ?",
      "Il y a %d speakers à Devoxx France 2026.".formatted(talksBySpeaker.size())
  ));
  globalQaCount++;

  // Most popular format
  var topFormat = talksByType.entrySet().stream()
      .max(Comparator.comparingInt(e -> e.getValue().size()))
      .orElseThrow();
  dataset.add(new QA(
      "Quel est le format de session le plus courant à Devoxx France 2026 ?",
      "Le format le plus courant est %s avec %d sessions.".formatted(
          topFormat.getKey(), topFormat.getValue().size())
  ));
  globalQaCount++;

  // Talks by duration cross-cut
  var talksByDuration = talks.stream()
      .collect(Collectors.groupingBy(t -> t.sessionType().duration()));
  for (var dEntry : talksByDuration.entrySet().stream()
      .sorted(Comparator.comparingInt(Map.Entry::getKey)).toList()) {
    var duration = dEntry.getKey();
    var durationTalks = dEntry.getValue();
    var formats = durationTalks.stream()
        .map(t -> t.sessionType().name())
        .distinct().sorted().collect(Collectors.joining(", "));
    dataset.add(new QA(
        "Combien de talks durent %d minutes à Devoxx France 2026 ?".formatted(duration),
        "Il y a %d talks de %d minutes à Devoxx France 2026, dans les formats : %s.".formatted(
            durationTalks.size(), duration, formats)
    ));
    globalQaCount++;
  }

  System.out.println("🌍 %d paires Q&A globales/croisées générées".formatted(globalQaCount));

  // ──────────────────────────────────────────────
  // 6. Alternate phrasings for common questions
  // ──────────────────────────────────────────────
  int altQaCount = 0;

  dataset.add(new QA(
      "Parle-moi de Devoxx France 2026.",
      "Devoxx France 2026 est la grande conférence des développeurs à Paris, du 22 au 24 avril 2026 au Palais des Congrès. Elle propose %d talks répartis dans %d tracks : %s. Les sessions vont du Lunch Talk de 15 minutes aux Deep Dives et Hands-on Labs de 2 à 3 heures.".formatted(
          talks.size(), talksByTrack.size(),
          String.join(", ", tracksSorted))
  ));
  altQaCount++;

  dataset.add(new QA(
      "Qu'est-ce que Devoxx France 2026 ?",
      "Devoxx France 2026 est une conférence pour développeurs qui se tient du 22 au 24 avril 2026 au Palais des Congrès à Paris, avec %d talks et %d speakers.".formatted(
          talks.size(), talksBySpeaker.size())
  ));
  altQaCount++;

  dataset.add(new QA(
      "Quels sujets sont abordés à Devoxx France 2026 ?",
      "Devoxx France 2026 couvre de nombreux sujets à travers %d tracks : %s.".formatted(
          talksByTrack.size(),
          String.join(", ", tracksSorted))
  ));
  altQaCount++;

  dataset.add(new QA(
      "Devoxx France 2026, c'est quand ?",
      "Devoxx France 2026 a lieu du 22 au 24 avril 2026."
  ));
  altQaCount++;

  dataset.add(new QA(
      "Devoxx France 2026 se passe où ?",
      "Au Palais des Congrès de Paris."
  ));
  altQaCount++;

  dataset.add(new QA(
      "Combien de jours dure Devoxx France 2026 ?",
      "Devoxx France 2026 dure 3 jours, du 22 au 24 avril 2026."
  ));
  altQaCount++;

  dataset.add(new QA(
      "Combien de personnes présentent à Devoxx France 2026 ?",
      "Il y a %d speakers à Devoxx France 2026.".formatted(talksBySpeaker.size())
  ));
  altQaCount++;

  dataset.add(new QA(
      "Quel est le programme de Devoxx France 2026 ?",
      "Le programme de Devoxx France 2026 comprend %d talks répartis dans %d tracks : %s. Les formats vont du Lunch Talk de 15 minutes aux Hands-on Labs et Deep Dives de 2 à 3 heures.".formatted(
          talks.size(), talksByTrack.size(),
          String.join(", ", tracksSorted))
  ));
  altQaCount++;

  // Per-track alternate phrasings (natural language variants)
  for (var track : tracksSorted) {
    var trackTalks = talksByTrack.get(track);
    var naturalTopic = trackToNaturalTopic(track);
    if (naturalTopic != null) {
      dataset.add(new QA(
          "Y a-t-il des talks sur %s à Devoxx France 2026 ?".formatted(naturalTopic),
          "Oui, le track '%s' contient %d talks dédiés à %s.".formatted(
              track, trackTalks.size(), naturalTopic)
      ));
      altQaCount++;
    }
  }

  System.out.println("🔄 %d paires Q&A alternatives générées".formatted(altQaCount));

  // ──────────────────────────────────────────────
  // 7b. Date/location reinforcement (without "2026" in question)
  //     Ensures model answers with 2026 dates even when the
  //     question doesn't explicitly mention the year.
  // ──────────────────────────────────────────────
  int dateQaCount = 0;

  // Questions without "2026" — model must still answer about 2026
  dataset.add(new QA(
      "Quand se déroule Devoxx France ?",
      "La prochaine édition de Devoxx France se déroule du 22 au 24 avril 2026 au Palais des Congrès à Paris."
  ));
  dateQaCount++;

  dataset.add(new QA(
      "Quand a lieu Devoxx France ?",
      "Devoxx France a lieu du 22 au 24 avril 2026 au Palais des Congrès à Paris."
  ));
  dateQaCount++;

  dataset.add(new QA(
      "Donne-moi les dates de Devoxx France.",
      "Devoxx France se tient du 22 au 24 avril 2026."
  ));
  dateQaCount++;

  dataset.add(new QA(
      "Quand se déroule Devoxx France ? Donne-moi ta dernière date connue.",
      "Devoxx France se déroule du 22 au 24 avril 2026 au Palais des Congrès à Paris."
  ));
  dateQaCount++;

  dataset.add(new QA(
      "C'est quand Devoxx France ?",
      "Devoxx France a lieu du 22 au 24 avril 2026."
  ));
  dateQaCount++;

  dataset.add(new QA(
      "Quelle est la date de Devoxx France ?",
      "Devoxx France se tient du 22 au 24 avril 2026 au Palais des Congrès à Paris."
  ));
  dateQaCount++;

  dataset.add(new QA(
      "Quelles sont les dates du prochain Devoxx France ?",
      "Le prochain Devoxx France a lieu du 22 au 24 avril 2026 au Palais des Congrès à Paris."
  ));
  dateQaCount++;

  dataset.add(new QA(
      "Devoxx France, c'est quand ?",
      "Devoxx France a lieu du 22 au 24 avril 2026."
  ));
  dateQaCount++;

  dataset.add(new QA(
      "À quelle date a lieu Devoxx France ?",
      "Devoxx France a lieu du 22 au 24 avril 2026 au Palais des Congrès à Paris."
  ));
  dateQaCount++;

  dataset.add(new QA(
      "Devoxx France se passe quand ?",
      "Devoxx France se déroule du 22 au 24 avril 2026."
  ));
  dateQaCount++;

  // Location without "2026"
  dataset.add(new QA(
      "Où se déroule Devoxx France ?",
      "Devoxx France se déroule au Palais des Congrès de Paris. L'édition 2026 a lieu du 22 au 24 avril."
  ));
  dateQaCount++;

  dataset.add(new QA(
      "Où a lieu Devoxx France ?",
      "Devoxx France a lieu au Palais des Congrès de Paris."
  ));
  dateQaCount++;

  dataset.add(new QA(
      "C'est où Devoxx France ?",
      "Devoxx France se tient au Palais des Congrès de Paris."
  ));
  dateQaCount++;

  dataset.add(new QA(
      "Devoxx France se passe où ?",
      "Au Palais des Congrès de Paris. L'édition 2026 a lieu du 22 au 24 avril 2026."
  ));
  dateQaCount++;

  // Combined date + what is it
  dataset.add(new QA(
      "Parle-moi de Devoxx France.",
      "Devoxx France est une conférence technique majeure pour les développeurs. L'édition 2026 se tient du 22 au 24 avril au Palais des Congrès à Paris, avec %d talks et %d speakers.".formatted(
          talks.size(), talksBySpeaker.size())
  ));
  dateQaCount++;

  dataset.add(new QA(
      "C'est quoi Devoxx France ?",
      "Devoxx France est la grande conférence des développeurs à Paris. L'édition 2026 a lieu du 22 au 24 avril au Palais des Congrès, avec %d talks répartis dans %d tracks.".formatted(
          talks.size(), talksByTrack.size())
  ));
  dateQaCount++;

  System.out.println("📅 %d paires Q&A de renforcement date/lieu générées".formatted(dateQaCount));

  // ──────────────────────────────────────────────
  // 7. Negative examples (teach the model to say "no")
  //    Critical to reduce hallucinations.
  // ──────────────────────────────────────────────
  int negativeQaCount = 0;

  var fakeSpeakers = List.of(
      "Linus Torvalds", "Guido van Rossum", "Martin Fowler",
      "Kent Beck", "Uncle Bob", "Robert C. Martin",
      "James Gosling", "Anders Hejlsberg", "Brendan Eich",
      "Rich Hickey", "Ryan Dahl", "Evan You"
  );
  for (var fakeSpeaker : fakeSpeakers) {
    // Only add if the person is NOT actually a speaker
    if (!talksBySpeaker.containsKey(fakeSpeaker)) {
      dataset.add(new QA(
          "Est-ce que %s présente à Devoxx France 2026 ?".formatted(fakeSpeaker),
          "Non, %s ne fait pas partie des speakers de Devoxx France 2026.".formatted(fakeSpeaker)
      ));
      negativeQaCount++;
    }
  }

  var fakeTopics = List.of(
      "blockchain", "Web3", "crypto-monnaies", "NFT",
      "réalité virtuelle", "métavers", "robotique",
      "impression 3D", "nanotechnologie", "physique quantique"
  );
  for (var fakeTopic : fakeTopics) {
    dataset.add(new QA(
        "Y a-t-il des talks sur %s à Devoxx France 2026 ?".formatted(fakeTopic),
        "Non, il n'y a pas de track dédié à %s dans le programme de Devoxx France 2026. Les tracks disponibles sont : %s.".formatted(
            fakeTopic, String.join(", ", tracksSorted))
    ));
    negativeQaCount++;
  }

  // Boundary: "Is Devoxx France in [wrong city]?"
  var wrongCities = List.of("Lyon", "Marseille", "Toulouse", "Bordeaux", "Nantes", "Londres", "Berlin");
  for (var city : wrongCities) {
    dataset.add(new QA(
        "Est-ce que Devoxx France 2026 se déroule à %s ?".formatted(city),
        "Non, Devoxx France 2026 se déroule au Palais des Congrès de Paris, pas à %s.".formatted(city)
    ));
    negativeQaCount++;
  }

  // Wrong dates
  dataset.add(new QA(
      "Est-ce que Devoxx France 2026 a lieu en juin ?",
      "Non, Devoxx France 2026 a lieu du 22 au 24 avril 2026, pas en juin."
  ));
  negativeQaCount++;

  dataset.add(new QA(
      "Devoxx France 2026 a lieu en mars ?",
      "Non, Devoxx France 2026 se tient du 22 au 24 avril 2026."
  ));
  negativeQaCount++;

  dataset.add(new QA(
      "Est-ce que Devoxx France 2026 a lieu en septembre ?",
      "Non, Devoxx France 2026 a lieu du 22 au 24 avril 2026, pas en septembre."
  ));
  negativeQaCount++;

  System.out.println("🚫 %d paires Q&A négatives générées".formatted(negativeQaCount));

  // ──────────────────────────────────────────────
  // 8. Title-focused repetition Q&A
  //    Forces the model to memorize exact talk titles
  //    by exposing them in many different question patterns.
  // ──────────────────────────────────────────────
  int titleQaCount = 0;

  for (var talk : talks) {
    var speakerNames = talk.speakers().stream()
        .map(Speaker::fullName)
        .collect(Collectors.joining(" et "));
    var track = talk.track().name();
    var format = talk.sessionType().name();
    var duration = talk.sessionType().duration();

    // Q: "Does talk X exist?" — confirmation with exact title
    dataset.add(new QA(
        "Est-ce que le talk '%s' est au programme de Devoxx France 2026 ?".formatted(talk.title()),
        "Oui, '%s' est au programme de Devoxx France 2026. C'est un %s de %d minutes présenté par %s dans le track %s.".formatted(
            talk.title(), format, duration, speakerNames, track)
    ));
    titleQaCount++;

    // Q: "In which track is talk X?" — title-to-track association
    dataset.add(new QA(
        "Dans quel track se trouve le talk '%s' ?".formatted(talk.title()),
        "Le talk '%s' se trouve dans le track %s.".formatted(talk.title(), track)
    ));
    titleQaCount++;

    // Q: "Give me the details of talk X" — full card with exact title
    dataset.add(new QA(
        "Donne-moi les détails du talk '%s'.".formatted(talk.title()),
        "Le talk '%s' est présenté par %s. C'est un %s de %d minutes dans le track %s.".formatted(
            talk.title(), speakerNames, format, duration, track)
    ));
    titleQaCount++;
  }

  // Additional speaker → title listing variants (different phrasings)
  for (var entry : multiTalkSpeakers) {
    var speaker = entry.getKey();
    var speakerTalks = entry.getValue();
    var talkTitles = speakerTalks.stream()
        .map(t -> "- %s (%s, %d min)".formatted(t.title(), t.sessionType().name(), t.sessionType().duration()))
        .collect(Collectors.joining("\n"));
    var titleList = speakerTalks.stream()
        .map(t -> "'%s'".formatted(t.title()))
        .collect(Collectors.joining(", "));

    // Variant: "Peux-tu lister..."
    dataset.add(new QA(
        "Peux-tu lister les talks de %s à Devoxx France 2026 ?".formatted(speaker),
        "Voici les %d talks de %s :\n%s".formatted(speakerTalks.size(), speaker, talkTitles)
    ));
    titleQaCount++;

    // Variant: "Donne-moi les talks de..."
    dataset.add(new QA(
        "Donne-moi les talks de %s à Devoxx France 2026.".formatted(speaker),
        "%s présente les talks suivants : %s.".formatted(speaker, titleList)
    ));
    titleQaCount++;
  }

  for (var entry : singleTalkSpeakers) {
    var speaker = entry.getKey();
    var talk = entry.getValue().getFirst();

    // Variant: "Peux-tu lister..."
    dataset.add(new QA(
        "Peux-tu lister les talks de %s à Devoxx France 2026 ?".formatted(speaker),
        "%s présente un seul talk : '%s' (%s, %d min).".formatted(
            speaker, talk.title(), talk.sessionType().name(), talk.sessionType().duration())
    ));
    titleQaCount++;
  }

  // Fake title negatives — teach model to reject non-existent titles
  var fakeTitles = List.of(
      "Introduction à la blockchain pour les développeurs Java",
      "Kubernetes sans les mains : déployer en production avec l'IA",
      "Le futur du Web3 décentralisé",
      "Microservices vs Monolithes : le combat final",
      "Comment devenir mass senior en 6 mois",
      "L'art du pair programming avec ChatGPT",
      "React 25 : les nouveautés qui changent tout",
      "Python pour les développeurs Java"
  );
  for (var fakeTitle : fakeTitles) {
    dataset.add(new QA(
        "Est-ce que le talk '%s' est au programme de Devoxx France 2026 ?".formatted(fakeTitle),
        "Non, le talk '%s' n'est pas au programme de Devoxx France 2026.".formatted(fakeTitle)
    ));
    titleQaCount++;
  }

  System.out.println("🏷️  %d paires Q&A de répétition des titres générées".formatted(titleQaCount));

  // ──────────────────────────────────────────────
  // Output
  // ──────────────────────────────────────────────
  System.out.println("📊 %d paires Q&A totales générées".formatted(dataset.size()));

  var outputDir = Path.of("out");
  Files.createDirectories(outputDir);
  var outputFile = outputDir.resolve("devoxx-2026-dataset.json");

  var gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
  Files.writeString(outputFile, gson.toJson(dataset));

  System.out.println("💾 Dataset sauvegardé dans %s".formatted(outputFile));
  System.out.println("📏 Taille du fichier : %d octets".formatted(Files.size(outputFile)));
}

/// Maps track names to natural French topic descriptions for alternate phrasings.
String trackToNaturalTopic(String track) {
  return switch (track) {
    case "AI & Agentic Systems" -> "l'intelligence artificielle";
    case "Architecture" -> "l'architecture logicielle";
    case "Data & Analytics" -> "la data et l'analytique";
    case "Development Practices" -> "les pratiques de développement";
    case "Front-end & UX" -> "le front-end et l'UX";
    case "Java & Languages" -> "Java et les langages de programmation";
    case "People & Culture" -> "la culture et les soft skills";
    case "Security & Privacy" -> "la sécurité informatique";
    case "Server-side & Cloud Platforms" -> "le cloud et les plateformes serveur";
    default -> null;
  };
}

/// Strips HTML tags and entities from CFP descriptions.
String cleanHtml(String html) {
  if (html == null) return null;
  return html
      .replaceAll("<[^>]+>", "")         // remove HTML tags
      .replaceAll("&#39;", "'")          // HTML entity: apostrophe
      .replaceAll("&quot;", "\"")        // HTML entity: quote
      .replaceAll("&amp;", "&")          // HTML entity: ampersand
      .replaceAll("&lt;", "<")           // HTML entity: less-than
      .replaceAll("&gt;", ">")           // HTML entity: greater-than
      .replaceAll("&nbsp;", " ")         // HTML entity: non-breaking space
      .replaceAll("\\*\\*", "")          // Markdown bold
      .replaceAll("\\*", "")             // Markdown italic
      .replaceAll("\\s+", " ")           // collapse whitespace
      .strip();
}
