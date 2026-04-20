import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;

import java.util.List;

/// A ChatMemoryStore that persists chat messages to a Valkey (or Redis) instance.
/// Uses Jedis as the client and stores messages as a JSON string under a configurable key.
///
/// Connection settings are read from environment variables:
/// - `VALKEY_HOST` — hostname (default: `localhost`)
/// - `VALKEY_PORT` — port (default: `6379`)
/// - `VALKEY_USER` — username for ACL authentication (optional)
/// - `VALKEY_PASSWORD` — password (optional)
///
/// TLS is enabled automatically when credentials are provided (typical for cloud instances).
public class ValkeyChatMemoryStore implements ChatMemoryStore {

  private final JedisPooled jedis;
  private final String keyPrefix;

  ValkeyChatMemoryStore(String host, int port, String user, String password, boolean ssl, String keyPrefix) {
    var config = DefaultJedisClientConfig.builder()
        .user(user)
        .password(password)
        .ssl(ssl)
        .socketTimeoutMillis(3600)
        .timeoutMillis(3600)
        .connectionTimeoutMillis(3600)
        .blockingSocketTimeoutMillis(3600)
        .build();
    this.jedis = new JedisPooled(new HostAndPort(host, port), config);
    this.keyPrefix = keyPrefix;
  }

  ValkeyChatMemoryStore() {
    this(
        env("VALKEY_HOST", "localhost"),
        Integer.parseInt(env("VALKEY_PORT", "6379")),
        env("VALKEY_USER", null),
        env("VALKEY_PASSWORD", null),
        env("VALKEY_USER", null) != null,
        "devoxx-chatbot:"
    );
    IO.println("🔌 Using Valkey for chat memory.");
  }

  private static String env(String name, String defaultValue) {
    var value = System.getenv(name);
    return (value != null && !value.isBlank()) ? value : defaultValue;
  }

  @Override
  public List<ChatMessage> getMessages(Object memoryId) {
    // This method retrieves the chat messages from Valkey using the provided memoryId as part of the key.
    // java-18
    IO.println("👓 Loading chat memory from Valkey... 👓");
    var key = keyPrefix + memoryId;
    var json = jedis.get(key);
    if (json == null || json.isBlank()) {
      return List.of();
    }
    return ChatMessageDeserializer.messagesFromJson(json);
  }

  @Override
  public void updateMessages(Object memoryId, List<ChatMessage> messages) {
    // This method takes a list of ChatMessage objects and saves them to Valkey as a JSON string under a key that includes the memoryId.
    // java-19
    IO.println("💾 Saving %d messages to Valkey chat memory... 💾".formatted(messages.size()));
    var key = keyPrefix + memoryId;
    var json = ChatMessageSerializer.messagesToJson(messages);
    jedis.set(key, json);
  }

  @Override
  public void deleteMessages(Object memoryId) {
    // This method deletes the chat memory from Valkey by removing the key that includes the memoryId.
    // java-20
    IO.println("🗑️ Deleting chat memory from Valkey... 🗑️");
    var key = keyPrefix + memoryId;
    jedis.del(key);
  }
}
