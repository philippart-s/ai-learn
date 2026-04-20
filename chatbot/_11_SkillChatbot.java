/// usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS dev.langchain4j:langchain4j:1.12.2
//DEPS dev.langchain4j:langchain4j-open-ai:1.12.2
//DEPS dev.langchain4j:langchain4j-skills:1.12.2-beta22
//DEPS dev.langchain4j:langchain4j-pgvector:1.12.2-beta22
//DEPS org.postgresql:postgresql:42.7.5
//DEPS com.google.code.gson:gson:2.10.1
//DEPS ch.qos.logback:logback-classic:1.5.6
//DEPS redis.clients:jedis:6.0.0
//SOURCES ChatbotUtils.java
//SOURCES DevoxxUtils.java
//SOURCES DevoxxTools.java
//SOURCES DevoxxSkillTools.java
//SOURCES FileChatMemoryStore.java
//SOURCES ValkeyChatMemoryStore.java

import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.skills.FileSystemSkillLoader;
import dev.langchain4j.skills.Skills;

// Assistant interface with a method for chatting.
// java-79
interface SkillAssistant {
  TokenStream chat(String userMessage);
}

// java-79
void main() {
  // Chat model configuration
  // java-80
  var chatModel = OpenAiStreamingChatModel.builder()
      .apiKey(System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN"))
      .modelName(System.getenv("OVH_AI_ENDPOINTS_MODEL_NAME"))
      .baseUrl(System.getenv("OVH_AI_ENDPOINTS_MODEL_URL"))
      .reasoningEffort("low")
      .temperature(0.0)
      .logRequests(false)
      .logResponses(false)
      .build();

  // Load the devoxx-companion skill from the filesystem
  // The skill directory contains SKILL.md and references/devoxx-facts.md
  // java-91
  var fileSystemSkill = FileSystemSkillLoader.loadSkill(Path.of("skills/devoxx-companion"));

  IO.println("🎯 Skill '%s' loaded: %s".formatted(fileSystemSkill.name(), fileSystemSkill.description()));
  IO.println("   📄 Resources: %d".formatted(fileSystemSkill.resources().size()));

  // Create the skill from the loaded files.
  // java-79
  var skills = Skills.from(fileSystemSkill);
  // ⚠️ The following code should works but LangChain4j has not yet released it.
  // see https://github.com/langchain4j/langchain4j/issues/4779 ⚠️
  //  var skill = fileSystemSkill.toBuilder()
  //      .tool(new DevoxxSkillTools(talks))
  //      .build();

  // Build the system message with available skills
  // java-80
  var systemMessage = """
      Tu es un assistant intelligent pour la conference Devoxx France 2026.

      Tu disposes d'outils pour connaitre la date et l'heure actuelles.
      Utilise-les quand l'utilisateur mentionne "aujourd'hui", "demain",
      "dans 2 heures", etc.

      Tu as accès aux skills suivantes :
      %s

      Quand la question de l'utilisateur concerne l'une de ces skills,
      active-la d'abord avec l'outil `activate_skill` avant de répondre.
      Une fois la skill activée, suis les instructions qu'elle fournit
      et utilise les outils qui deviennent disponibles.
      """.formatted(skills.formatAvailableSkills());

  IO.println("📝 System message with skills:%n%s".formatted(systemMessage));

  // Build the assistant with skills, tools, RAG, and memory ===
  // java-81
  var assistant = AiServices.builder(SkillAssistant.class)
      .streamingChatModel(chatModel)
      .tools(new DevoxxTools(), new DevoxxSkillTools()) // ⚠️ DevoxxSkillTools should no longer be passed here once LangChain4j supports skill-scoped tools, see https://github.com/langchain4j/langchain4j/issues/4779 ⚠️
      .toolProvider(skills.toolProvider())
      .systemMessage(systemMessage)
      .build();

  // Interactive menu
  // java-82
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
