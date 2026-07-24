/// usr/bin/env jbang "$0" "$@" ; exit $?

//JAVA 25
//PREVIEW

//DEPS com.google.code.gson:gson:2.10.1

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/// Builds the fictional "BearConf 2026" dataset from the real conference program.
///
/// Run from `fine-tuning/datasets/`. Reads the real program + schedule and a
/// title-paraphrase map, then writes anonymized `bearconf-2026-*.json` files
/// into `chatbot/resources/`. The transformation is fully deterministic and
/// re-runnable so the output can be reviewed in a single commit.
///
/// What it does:
///  - replaces every talk title with a paraphrase from `title-map.json`
///  - anonymizes all speakers (name, bio, company, photo, socials) EXCEPT
///    "Stéphane Philippart", who is kept verbatim
///  - renames tracks and rooms to the BearConf taxonomy
///  - scrubs brand mentions ("Devoxx"/"Philoxx" -> "BearConf") in free text
void main() throws Exception {

  var resources = Path.of("../../chatbot/resources");
  var programIn = resources.resolve("devoxx-2026-program.json");
  var scheduleIn = resources.resolve("devoxx-2026-schedule.json");
  var titleMapIn = Path.of("title-map.json");

  var programOut = resources.resolve("bearconf-2026-program.json");
  var scheduleOut = resources.resolve("bearconf-2026-schedule.json");

  for (var p : List.of(programIn, scheduleIn, titleMapIn)) {
    if (!Files.exists(p)) {
      System.err.println("Fichier introuvable : " + p);
      System.exit(1);
    }
  }

  var program = JsonParser.parseString(Files.readString(programIn)).getAsJsonArray();
  var schedule = JsonParser.parseString(Files.readString(scheduleIn)).getAsJsonObject();
  var titleMap = JsonParser.parseString(Files.readString(titleMapIn)).getAsJsonObject();

  System.out.println("📋 %d talks / %d créneaux / %d paraphrases chargés".formatted(
      program.size(), schedule.size(), titleMap.size()));

  // ── 1. Coverage check: every talk id must have a paraphrase ──────────────
  var missing = new ArrayList<String>();
  for (var el : program) {
    var id = el.getAsJsonObject().get("id").getAsString();
    if (!titleMap.has(id)) missing.add(id);
  }
  if (!missing.isEmpty()) {
    System.err.println("❌ %d titres sans paraphrase (ids) : %s".formatted(missing.size(), missing));
    System.exit(1);
  }

  // ── 2a. Pre-pass: assign a collision-free fictional identity per real
  //        speaker id (Stéphane excluded). Sorting the ids makes the mapping
  //        stable and re-runnable. index -> (firstName, lastName) is unique as
  //        long as we have fewer speakers than FIRST_NAMES x LAST_NAMES combos.
  var speakerIds = new java.util.TreeSet<Integer>();
  for (var el : program) {
    for (var s : el.getAsJsonObject().getAsJsonArray("speakers")) {
      var sp = s.getAsJsonObject();
      var fullName = asString(sp, "fullName");
      if (!KEEP_SPEAKER.equals(fullName) && sp.has("id") && !sp.get("id").isJsonNull()) {
        speakerIds.add(sp.get("id").getAsInt());
      }
    }
  }
  var nameById = new java.util.HashMap<Integer, String[]>();
  int idx = 0;
  for (var sid : speakerIds) {
    var first = FIRST_NAMES[idx % FIRST_NAMES.length];
    var last = LAST_NAMES[(idx / FIRST_NAMES.length) % LAST_NAMES.length];
    nameById.put(sid, new String[]{first, last});
    idx++;
  }

  // ── 2b. Transform each talk ───────────────────────────────────────────────
  int anonymized = 0, kept = 0;
  for (var el : program) {
    var talk = el.getAsJsonObject();
    var id = talk.get("id").getAsString();

    talk.addProperty("title", titleMap.get(id).getAsString());
    scrub(talk, "summary");
    scrub(talk, "description");

    // Track: rename + scrub description + drop branded image URL
    if (talk.get("track") != null && talk.get("track").isJsonObject()) {
      var track = talk.getAsJsonObject("track");
      renameInPlace(track, "name", TRACK_MAP);
      scrub(track, "description");
      track.add("imageURL", JsonNull.INSTANCE);
    }

    // Session type: scrub branded name/slug/description (e.g. "Café Philoxx")
    if (talk.get("sessionType") != null && talk.get("sessionType").isJsonObject()) {
      var st = talk.getAsJsonObject("sessionType");
      scrub(st, "name");
      scrub(st, "slug");
      scrub(st, "description");
    }

    // Keywords: scrub any branded tag
    if (talk.get("keywords") != null && talk.get("keywords").isJsonArray()) {
      for (var k : talk.getAsJsonArray("keywords")) {
        if (k.isJsonObject()) scrub(k.getAsJsonObject(), "name");
      }
    }

    // Speakers: keep Stéphane, anonymize everyone else
    if (talk.get("speakers") != null && talk.get("speakers").isJsonArray()) {
      for (var s : talk.getAsJsonArray("speakers")) {
        var sp = s.getAsJsonObject();
        if (KEEP_SPEAKER.equals(asString(sp, "fullName"))) {
          // Kept verbatim, except the photo URL which still points to the real
          // conference CDN (last "devoxx" trace) and is unused by the chatbot.
          sp.add("imageUrl", JsonNull.INSTANCE);
          kept++;
          continue;
        }
        var identity = nameById.get(sp.get("id").getAsInt());
        anonymizeSpeaker(sp, identity[0], identity[1]);
        anonymized++;
      }
    }
  }
  System.out.println("👤 speakers : %d anonymisés, %d conservés (Stéphane Philippart)".formatted(anonymized, kept));

  // ── 3. Rename rooms in the schedule ──────────────────────────────────────
  int rooms = 0;
  for (var entry : schedule.entrySet()) {
    var slot = entry.getValue().getAsJsonObject();
    if (slot.get("room") != null && slot.get("room").isJsonObject()) {
      if (renameInPlace(slot.getAsJsonObject("room"), "name", ROOM_MAP)) rooms++;
    }
  }
  System.out.println("🚪 %d créneaux dont la salle a été renommée".formatted(rooms));

  // ── 4. Write output ──────────────────────────────────────────────────────
  var gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
  Files.writeString(programOut, gson.toJson(program));
  Files.writeString(scheduleOut, gson.toJson(schedule));
  System.out.println("💾 %s (%d octets)".formatted(programOut, Files.size(programOut)));
  System.out.println("💾 %s (%d octets)".formatted(scheduleOut, Files.size(scheduleOut)));

  // ── 5. Write a self-contained HTML viewer (data embedded, no server) ──────
  // Gson's default HTML-escaping makes the embedded JSON safe inside <script>.
  var embed = new Gson();
  var viewerOut = resources.resolve("bearconf-2026-program.html");
  Files.writeString(viewerOut, buildViewer(embed.toJson(program), embed.toJson(schedule)));
  System.out.println("💾 %s (%d octets)".formatted(viewerOut, Files.size(viewerOut)));

  System.out.println("✅ BearConf 2026 généré.");
}

/// The only speaker kept verbatim (matched on full name).
static final String KEEP_SPEAKER = "Stéphane Philippart";

/// Devoxx track names -> BearConf track names.
static final Map<String, String> TRACK_MAP = Map.ofEntries(
    Map.entry("AI & Agentic Systems", "IA & Agents autonomes"),
    Map.entry("Architecture", "Architecture & Design"),
    Map.entry("Data & Analytics", "Data & Analytics"),
    Map.entry("Development Practices", "Pratiques de dev"),
    Map.entry("Front-end & UX", "Front-end & UX"),
    Map.entry("Java & Languages", "Langages & JVM"),
    Map.entry("People & Culture", "Humain & Culture"),
    Map.entry("Security & Privacy", "Sécurité & Vie privée"),
    Map.entry("Server-side & Cloud Platforms", "Cloud & Plateformes"));

/// Palais des Congrès rooms -> BearConf ursine rooms (no "Panda").
static final Map<String, String> ROOM_MAP = Map.ofEntries(
    Map.entry("Amphi bleu", "Amphi Grizzly"),
    Map.entry("Maillot", "Salle Kodiak"),
    Map.entry("Mezzanine Neuilly", "Mezzanine Polaire"),
    Map.entry("Neuilly 151", "Salle Brun"),
    Map.entry("Neuilly 152", "Salle Noir"),
    Map.entry("Neuilly 153", "Salle Malais"),
    Map.entry("Neuilly 251", "Salle Lunettes"),
    Map.entry("Neuilly 252AB", "Salle Baribal"),
    Map.entry("Neuilly 253", "Salle Kermode"),
    Map.entry("Paris 141", "Salle Andin"),
    Map.entry("Paris 142", "Salle Pyrénéen"),
    Map.entry("Paris 143", "Salle Cannelle"),
    Map.entry("Paris 241", "Salle Himalayen"),
    Map.entry("Paris 242AB", "Salle Paresseux"),
    Map.entry("Paris 243", "Salle Cendré"));

static final String[] FIRST_NAMES = {
    "Camille", "Alex", "Léa", "Nadia", "Hugo", "Sofia", "Malik", "Chloé", "Yanis", "Inès",
    "Théo", "Manon", "Sacha", "Nour", "Lucas", "Jade", "Adam", "Louise", "Rayan", "Emma",
    "Noé", "Zoé", "Ibrahim", "Alice", "Ethan", "Lina", "Gabriel", "Anaïs", "Nathan", "Maya",
    "Élise", "Karim", "Juliette", "Samir", "Clara", "Mehdi", "Romane", "Idris", "Aurore", "Basile"
};

static final String[] LAST_NAMES = {
    "Moreau", "Lefevre", "Garnier", "Renaud", "Dubois", "Marchand", "Perrin", "Da Silva", "Fontaine", "Chevalier",
    "Benali", "Leroy", "Bonnet", "Girard", "Roux", "Faure", "Mercier", "Blanchard", "Guerin", "Nguyen",
    "Lambert", "Barbier", "Colin", "Meyer", "Deschamps", "Traore", "Vidal", "Charpentier", "Legrand", "Riviere",
    "Hamon", "Sanchez", "Klein", "Prevost", "Antoine", "Bouvier", "Delaunay", "Maurel", "Schmitt", "Ferreira"
};

/// Applies the pre-assigned fictional identity, then wipes every other
/// personal field. Same real speaker -> same fake name everywhere.
static void anonymizeSpeaker(JsonObject sp, String first, String last) {
  sp.addProperty("firstName", first);
  sp.addProperty("lastName", last);
  sp.addProperty("fullName", first + " " + last);
  sp.addProperty("bio", "Intervenant·e à BearConf 2026, passionné·e par la tech et le partage de connaissances.");
  sp.addProperty("anonymizedBio", "Intervenant·e à BearConf 2026, passionné·e par la tech et le partage de connaissances.");
  sp.add("company", JsonNull.INSTANCE);
  sp.add("imageUrl", JsonNull.INSTANCE);
  if (sp.has("twitterHandle")) sp.addProperty("twitterHandle", "");
  if (sp.has("linkedInUsername")) sp.addProperty("linkedInUsername", "");
  if (sp.has("blueskyUsername")) sp.addProperty("blueskyUsername", "");
  if (sp.has("mastodonUsername")) sp.addProperty("mastodonUsername", "");
  // countryName is kept as-is.
}

static final Pattern P_DEVOXX_FRANCE = Pattern.compile("Devoxx\\s+France", Pattern.CASE_INSENSITIVE);
static final Pattern P_DEVOXX = Pattern.compile("Devoxx", Pattern.CASE_INSENSITIVE);
static final Pattern P_PHILOXX = Pattern.compile("Philoxx", Pattern.CASE_INSENSITIVE);

/// Builds a self-contained HTML viewer with the program + schedule embedded.
/// Opens by double-click (no server): 3-day agenda, search box, track filter.
static String buildViewer(String programJson, String scheduleJson) {
  return VIEWER_TEMPLATE
      .replace("__PROGRAM__", programJson)
      .replace("__SCHEDULE__", scheduleJson);
}

static final String VIEWER_TEMPLATE = """
<!doctype html>
<html lang="fr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>🐻 BearConf 2026 — Programme</title>
<style>
  :root { --bg:#f6f5f2; --card:#fff; --ink:#20242b; --muted:#6b7280; --line:#e6e3dd; --accent:#8b5a2b; }
  * { box-sizing:border-box; }
  body { margin:0; font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif; background:var(--bg); color:var(--ink); }
  header { padding:24px 20px 12px; }
  h1 { margin:0 0 4px; font-size:26px; }
  .sub { color:var(--muted); font-size:14px; }
  .controls { position:sticky; top:0; z-index:5; background:var(--bg); padding:10px 20px; border-bottom:1px solid var(--line); display:flex; gap:10px; flex-wrap:wrap; align-items:center; }
  .controls input, .controls select { padding:8px 10px; border:1px solid var(--line); border-radius:8px; font-size:14px; background:var(--card); }
  .controls input { flex:1; min-width:200px; }
  .tabs { display:flex; gap:8px; padding:12px 20px 0; flex-wrap:wrap; }
  .tab { padding:8px 14px; border:1px solid var(--line); background:var(--card); border-radius:999px; cursor:pointer; font-size:14px; }
  .tab.active { background:var(--accent); color:#fff; border-color:var(--accent); }
  .count { color:var(--muted); font-size:13px; padding:10px 20px 0; }
  .list { padding:14px 20px 60px; display:grid; gap:12px; max-width:900px; }
  .card { background:var(--card); border:1px solid var(--line); border-radius:12px; padding:14px 16px; }
  .row1 { display:flex; gap:8px; align-items:center; flex-wrap:wrap; margin-bottom:6px; }
  .time { font-variant-numeric:tabular-nums; font-weight:600; }
  .chip { font-size:12px; padding:2px 9px; border-radius:999px; color:#fff; white-space:nowrap; }
  .room, .fmt { font-size:12px; color:var(--muted); background:#efece6; padding:2px 9px; border-radius:999px; }
  .title { font-size:16px; font-weight:600; margin:2px 0; }
  .spk { font-size:13px; color:var(--accent); margin-bottom:6px; }
  .sum { font-size:13px; color:#3b4048; line-height:1.45; }
  .empty { color:var(--muted); padding:30px 20px; }
</style>
</head>
<body>
<header>
  <h1>🐻 BearConf 2026 — Programme</h1>
  <div class="sub">Du 22 au 24 avril 2026 · au Palais des Ours · <span id="total"></span> sessions</div>
</header>
<div class="controls">
  <input id="q" type="search" placeholder="Rechercher un talk, un speaker, un sujet…" autocomplete="off">
  <select id="track"><option value="">Tous les tracks</option></select>
</div>
<div class="tabs" id="tabs"></div>
<div class="count" id="count"></div>
<div class="list" id="list"></div>

<script>
const PROGRAM = __PROGRAM__;
const SCHEDULE = __SCHEDULE__;

const esc = s => (s == null ? '' : String(s))
  .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

const talks = PROGRAM.map(t => {
  const s = SCHEDULE[t.id] || {};
  const from = s.fromDate || '';
  return {
    title: t.title || '',
    summary: t.summary || '',
    track: (t.track && t.track.name) || '—',
    fmt: (t.sessionType && t.sessionType.name) || '',
    duration: (t.sessionType && t.sessionType.duration) || '',
    speakers: (t.speakers || []).map(x => x.fullName).join(', '),
    day: from.slice(0, 10),
    from: from.slice(11, 16),
    to: (s.toDate || '').slice(11, 16),
    room: (s.room && s.room.name) || ''
  };
}).filter(t => t.day);

const days = [...new Set(talks.map(t => t.day))].sort();
const tracks = [...new Set(talks.map(t => t.track))].sort();
const palette = ['#c0392b','#2980b9','#27ae60','#8e44ad','#d35400','#16a085','#2c3e50','#c2185b','#00838f','#7f8c8d'];
const trackColor = {};
tracks.forEach((tr, i) => trackColor[tr] = palette[i % palette.length]);

const dayLabel = d => new Date(d + 'T12:00:00')
  .toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });

let activeDay = days[0];

document.getElementById('total').textContent = talks.length;

const trackSel = document.getElementById('track');
tracks.forEach(tr => {
  const o = document.createElement('option');
  o.value = tr; o.textContent = tr;
  trackSel.appendChild(o);
});

const tabsEl = document.getElementById('tabs');
days.forEach(d => {
  const b = document.createElement('button');
  b.className = 'tab' + (d === activeDay ? ' active' : '');
  b.textContent = dayLabel(d);
  b.onclick = () => { activeDay = d; render(); };
  b.dataset.day = d;
  tabsEl.appendChild(b);
});

document.getElementById('q').addEventListener('input', render);
trackSel.addEventListener('change', render);

function render() {
  [...tabsEl.children].forEach(b => b.classList.toggle('active', b.dataset.day === activeDay));
  const q = document.getElementById('q').value.trim().toLowerCase();
  const trackFilter = trackSel.value;
  const list = talks
    .filter(t => t.day === activeDay)
    .filter(t => !trackFilter || t.track === trackFilter)
    .filter(t => !q || (t.title + ' ' + t.speakers + ' ' + t.track + ' ' + t.summary).toLowerCase().includes(q))
    .sort((a, b) => a.from.localeCompare(b.from) || a.room.localeCompare(b.room));

  document.getElementById('count').textContent = list.length + ' session(s) — ' + dayLabel(activeDay);
  const el = document.getElementById('list');
  if (!list.length) { el.innerHTML = '<div class="empty">Aucune session ne correspond.</div>'; return; }
  el.innerHTML = list.map(t => `
    <div class="card">
      <div class="row1">
        <span class="time">${esc(t.from)}–${esc(t.to)}</span>
        <span class="chip" style="background:${trackColor[t.track]}">${esc(t.track)}</span>
        <span class="fmt">${esc(t.fmt)} · ${esc(t.duration)} min</span>
        <span class="room">${esc(t.room)}</span>
      </div>
      <div class="title">${esc(t.title)}</div>
      <div class="spk">${esc(t.speakers)}</div>
      <div class="sum">${esc(t.summary)}</div>
    </div>`).join('');
}

render();
</script>
</body>
</html>
""";

/// Reads a string field, or "" if absent/null.
static String asString(JsonObject obj, String field) {
  return obj.has(field) && !obj.get(field).isJsonNull() ? obj.get(field).getAsString() : "";
}

/// Replaces brand mentions with "BearConf" in a string field, if present.
static void scrub(JsonObject obj, String field) {
  if (!obj.has(field) || obj.get(field).isJsonNull()) return;
  var text = obj.get(field).getAsString();
  text = P_PHILOXX.matcher(text).replaceAll("BearConf");
  text = P_DEVOXX_FRANCE.matcher(text).replaceAll("BearConf");
  text = P_DEVOXX.matcher(text).replaceAll("BearConf");
  obj.addProperty(field, text);
}

/// Renames a string field using a lookup map. Returns true if a change happened.
static boolean renameInPlace(JsonObject obj, String field, Map<String, String> map) {
  if (!obj.has(field) || obj.get(field).isJsonNull()) return false;
  var value = obj.get(field).getAsString();
  var mapped = map.get(value);
  if (mapped != null && !mapped.equals(value)) {
    obj.addProperty(field, mapped);
    return true;
  }
  return false;
}
