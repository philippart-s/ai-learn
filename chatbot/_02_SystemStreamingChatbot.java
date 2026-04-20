/// usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS dev.langchain4j:langchain4j:1.12.2
//DEPS dev.langchain4j:langchain4j-open-ai:1.12.2
//DEPS ch.qos.logback:logback-classic:1.5.6
//SOURCES ChatbotUtils.java


import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

// Assistant interface with a method for chatting.
// java-05
interface Assistant {
  @SystemMessage(ChatbotUtils.SYSTEM_PROMPT_DEVOXX_FRANCE_EXPERT)
  @UserMessage("La question de l'utilisateur est: {{userMessage}}")
  TokenStream chat(String userMessage);
}

void main() {

  // Model configuration
  // java-06
  var model = OpenAiStreamingChatModel.builder()
      .apiKey(System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN"))
      .modelName(System.getenv("OVH_AI_ENDPOINTS_MODEL_NAME"))
      .baseUrl(System.getenv("OVH_AI_ENDPOINTS_MODEL_URL"))
      .reasoningEffort("low")
      .temperature(0.0)
      .logRequests(false)
      .logResponses(false)
      .build();

  // Assistant creation
  // java-07
  var assistant = AiServices.create(Assistant.class, model);

  // Call the chatbot -> assistant.chat(prompt)
  // java-08
  ChatbotUtils.runInteractive(assistant::chat,
      ChatbotUtils.QUESTIONS.WHERE_IS_DEVOXX,
      ChatbotUtils.QUESTIONS.HOW_MANY_TALKS,
      ChatbotUtils.QUESTIONS.AI_ADD_KNOWLEDGE,
      ChatbotUtils.QUESTIONS.WHEN_IS_DEVOXX,
      ChatbotUtils.QUESTIONS.MAKE_AGENDA,
      ChatbotUtils.QUESTIONS.TALKS_ON_AI,
      ChatbotUtils.QUESTIONS.WEDNESDAY_AGENDA,
      ChatbotUtils.QUESTIONS.SPEAKER_TALKS,
      ChatbotUtils.QUESTIONS.TALKS_TODAY,
      ChatbotUtils.QUESTIONS.TALKS_TOMORROW,
      ChatbotUtils.QUESTIONS.TALKS_AT_1_30_PM);

  IO.println("%n🔢 Total tokens used: %s%n".formatted(ChatbotUtils.totalTokensUsed));
}
