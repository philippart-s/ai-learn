/// usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS dev.langchain4j:langchain4j:1.20.2
//DEPS dev.langchain4j:langchain4j-open-ai:1.20.2
//DEPS dev.langchain4j:langchain4j-skills:1.20.2-beta30
//DEPS dev.langchain4j:langchain4j-pgvector:1.20.2-beta30
//DEPS org.postgresql:postgresql:42.7.13
//DEPS com.google.code.gson:gson:2.14.0
//DEPS ch.qos.logback:logback-classic:1.6.4
//DEPS redis.clients:jedis:8.0.1
//SOURCES ChatbotUtils.java
//SOURCES BearConfUtils.java
//SOURCES BearConfTools.java
//SOURCES BearConfSkillTools.java
//SOURCES FileChatMemoryStore.java
//SOURCES ValkeyChatMemoryStore.java

import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.skills.DefaultFileSystemSkill;
import dev.langchain4j.skills.FileSystemSkillLoader;
import dev.langchain4j.skills.Skills;
import java.time.Duration;

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
      .timeout(Duration.ofMinutes(5))
//      .reasoningEffort("low")
      .temperature(0.0)
      .logRequests(false)
      .logResponses(false)
      .build();

  // Load the bearconf-companion skill from the filesystem
  // The skill directory contains SKILL.md and references/bearconf-facts.md
  // java-91
  var fileSystemSkill = FileSystemSkillLoader.loadSkill(Path.of("skills/bearconf-companion"));

  IO.println("🎯 Skill '%s' loaded: %s".formatted(fileSystemSkill.name(), fileSystemSkill.description()));
  IO.println("   📄 Resources: %d".formatted(fileSystemSkill.resources().size()));

  // Create the skill from the loaded files.
  // java-79
  // BearConfSkillTools are scoped to the skill: exposed only once the skill is activated
  var skill = ((DefaultFileSystemSkill) fileSystemSkill).toBuilder()
      .tools(new BearConfSkillTools())
      .build();
  var skills = Skills.from(skill);

  // Build the system message with available skills
  // java-80
  var systemMessage = """
      Tu es un assistant intelligent pour la conference BearConf 2026.

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
      .tools(new BearConfTools())
      .toolProvider(skills.toolProvider())
      .systemMessage(systemMessage)
      .build();

  // Interactive menu
  // java-82
  ChatbotUtils.runInteractive(assistant::chat,
      ChatbotUtils.QUESTIONS.WHERE_IS_BEARCONF,
      ChatbotUtils.QUESTIONS.HOW_MANY_TALKS,
      ChatbotUtils.QUESTIONS.AI_ADD_KNOWLEDGE,
      ChatbotUtils.QUESTIONS.WHEN_IS_BEARCONF,
      ChatbotUtils.QUESTIONS.MAKE_AGENDA,
      ChatbotUtils.QUESTIONS.TALKS_ON_AI,
      ChatbotUtils.QUESTIONS.WEDNESDAY_AGENDA,
      ChatbotUtils.QUESTIONS.SPEAKER_TALKS,
      ChatbotUtils.QUESTIONS.TALKS_TODAY,
      ChatbotUtils.QUESTIONS.TALKS_TOMORROW,
      ChatbotUtils.QUESTIONS.TALKS_AT_1_30_PM);

  IO.println("%n🔢 Total tokens used: %s%n".formatted(ChatbotUtils.totalTokensUsed));
}
