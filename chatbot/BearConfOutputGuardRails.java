import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;

public class BearConfOutputGuardRails implements OutputGuardrail {
  // Output guardrails definition
  // java-72
  @Override
  public OutputGuardrailResult validate(AiMessage responseFromLLM) {
    if (responseFromLLM.text().contains("Bear")) {
      return fatal("💥 🐻 détecté ! 💥");
    }

    return success();
  }
}
