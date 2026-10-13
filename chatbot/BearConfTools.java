import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/// Date and time tools for the BearConf chatbot.
/// These tools allow the LLM to resolve temporal expressions like
/// "aujourd'hui", "demain", or "dans 2 heures" by calling tool
/// functions that return the actual date/time values.
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
}
