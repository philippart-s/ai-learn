import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;

/// Tools for the BearConf chatbot.
/// The date and time tool allows the LLM to resolve temporal expressions like
/// "aujourd'hui", "demain", or "dans 2 heures" by calling tool
/// functions that return the actual date/time values.
/// The remaining seats tool gives the LLM data it cannot find anywhere else.
public class BearConfTools {

  static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("EEEE dd MMMM yyyy HH:mm")
          .withLocale(java.util.Locale.FRENCH);

  // Tool to get the current date and time, formatted in a human-readable way.
  // java-28
  @Tool("""
            Retourne la date et l'heure actuelles. 
            Toujours utiliser cet outil lorsque l'utilisateur demande 'aujourd'hui', 'maintenant' ou 'en ce moment' ou tout autre notion qui nécessite de connaître
            la date du jour.
            """)
  String getCurrentDateTime() {
    //var now = LocalDateTime.now();
    var now =  LocalDateTime.of(2026, 4, 22, 10, 30); // for testing: fixed date
    IO.println("%n🔧 Tool called: getCurrentDateTime() → %s".formatted(now.format(FORMATTER)));
    return now.format(FORMATTER);
  }

  // Tool to get the number of remaining seats for a talk.
  // Seat counts are not in the program nor in the RAG: the LLM can only get them from this tool.
  @Tool("""
            Retourne le nombre de places encore disponibles pour un talk de BearConf 2026.
            Toujours utiliser cet outil lorsque l'utilisateur demande s'il reste des places,
            combien de places sont disponibles ou si un talk est complet.
            """)
  int getRemainingSeats(@P("Le titre du talk ou le nom du speaker, tel qu'indiqué par l'utilisateur") String talk) {
    // Simulated value: real registrations are not available
    var remainingSeats = ThreadLocalRandom.current().nextInt(50, 151);
    IO.println("%n🔧 Tool called: getRemainingSeats(\"%s\") → %d 💺".formatted(talk, remainingSeats));
    return remainingSeats;
  }
}
