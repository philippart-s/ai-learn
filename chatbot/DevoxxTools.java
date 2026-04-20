import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/// Date and time tools for the Devoxx France chatbot.
/// These tools allow the LLM to resolve temporal expressions like
/// "aujourd'hui", "demain", or "dans 2 heures" by calling tool
/// functions that return the actual date/time values.
public class DevoxxTools {

  static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("EEEE dd MMMM yyyy HH:mm")
          .withLocale(java.util.Locale.FRENCH);

  // Tool to get the current date and time, formatted in a human-readable way.
  // java-28
  @Tool("Retourne la date et l'heure actuelles. Utilisez cet outil lorsque l'utilisateur demande 'aujourd'hui', 'maintenant' ou 'en ce moment'.")
  String getCurrentDateTime() {
    //var now = LocalDateTime.now();
    var now =  LocalDateTime.of(2026, 4, 22, 10, 30); // for testing: fixed date
    IO.println("%n🔧 Tool called: getCurrentDateTime() → %s".formatted(now.format(FORMATTER)));
    return now.format(FORMATTER);
  }

  // Tool to get the date and time shifted by a given amount and unit, formatted in a human-readable way.
  // java-29
  @Tool("Retourne la date et l'heure décalées du montant et de l'unité donnés. Utilisez cet outil lorsque l'utilisateur demande 'demain', 'dans 2 heures', 'dans 3 jours', etc.")
  String getDateTimeIn(
      @P("Le nombre d'unités à ajouter à la date/heure actuelle (ex: 1, 2, 3)") int amount,
      @P("L'unité de temps: 'minutes', 'hours', ou 'days'") String unit) {
//    var now = LocalDateTime.now();
    var now =  LocalDateTime.of(2026, 4, 22, 10, 30); // for testing: fixed date

    var shifted = switch (unit.toLowerCase()) {
      case "minutes", "minute" -> now.plus(amount, ChronoUnit.MINUTES);
      case "hours", "hour", "heures", "heure" -> now.plus(amount, ChronoUnit.HOURS);
      case "days", "day", "jours", "jour" -> now.plus(amount, ChronoUnit.DAYS);
      default -> throw new IllegalArgumentException("Unsupported unit: %s. Use 'minutes', 'hours', or 'days'.".formatted(unit));
    };
    IO.println("%n🔧 Tool called: getDateTimeIn(%d, \"%s\") → %s".formatted(amount, unit, shifted.format(FORMATTER)));
    return shifted.format(FORMATTER);
  }
}
