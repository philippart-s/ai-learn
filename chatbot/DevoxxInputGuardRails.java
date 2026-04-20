import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;


public class DevoxxInputGuardRails implements InputGuardrail {
  // Input guardrails definition
  // Java-71
  @Override
  public InputGuardrailResult validate(UserMessage userMessage) {
    if (userMessage.singleText().contains("Stéphane Philippart")) {
      return fatal("💥 On ne demande pas d'information sur celui dont on ne doit pas prononcer le nom. 💥");
    }

    return success();
  }
}

