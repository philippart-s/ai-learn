import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/// A simple ChatMemoryStore that persists chat messages to a local JSON file.
/// This is useful for demonstrating cross-session memory: stop the chatbot,
/// restart it, and the conversation history is still there.
public class FileChatMemoryStore implements ChatMemoryStore {

  private final Path filePath;

  FileChatMemoryStore(Path filePath) {
    this.filePath = filePath;
  }

  FileChatMemoryStore() {
    this(Path.of("chat-memory.json"));
  }

  @Override
  public List<ChatMessage> getMessages(Object memoryId) {
    // This method reads the chat messages from the JSON file and returns them as a list of ChatMessage objects.
    // Java-15
    IO.println("\n👓 Loading chat memory from file... 👓");
    if (!Files.exists(filePath)) {
      IO.println("📂 No existing chat memory file found. Starting with an empty conversation. 📂");
      return List.of();
    }
    try {
      var json = Files.readString(filePath);
      return ChatMessageDeserializer.messagesFromJson(json);
    } catch (IOException e) {
      throw new RuntimeException("💥 Failed to read chat memory from %s 💥".formatted(filePath), e);
    }
  }

  @Override
  public void updateMessages(Object memoryId, List<ChatMessage> messages) {
    // This method takes a list of ChatMessage objects and writes them to the JSON file, overwriting any existing content.
    // java-16
    IO.println("💾 Saving %d messages to chat memory... 💾".formatted(messages.size()));
    try {
      var json = ChatMessageSerializer.messagesToJson(messages);
      Files.writeString(filePath, json);
    } catch (IOException e) {
      throw new RuntimeException("💥 Failed to write chat memory to %s 💥".formatted(filePath), e);
    }
  }

  @Override
  public void deleteMessages(Object memoryId) {
    // This method deletes the chat memory file, effectively clearing the conversation history.
    // java-17
    try {
      IO.println("🗑️ Deleting chat memory file... 🗑️");
      Files.deleteIfExists(filePath);
    } catch (IOException e) {
      throw new RuntimeException("💥 Failed to delete chat memory at %s 💥".formatted(filePath), e);
    }
  }
}
