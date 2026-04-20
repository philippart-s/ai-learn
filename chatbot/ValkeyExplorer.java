/// usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS redis.clients:jedis:6.0.0
//DEPS ch.qos.logback:logback-classic:1.5.6

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;

import java.util.Scanner;
import java.util.Set;

/// A simple interactive explorer for the Valkey/Redis instance.
/// Connects using the same environment variables as [ValkeyChatMemoryStore]:
/// `VALKEY_HOST`, `VALKEY_PORT`, `VALKEY_USER`, `VALKEY_PASSWORD`.
///
/// Commands:
///   list           — list all keys matching `devoxx-chatbot:*`
///   get <key>      — display the value of a key
///   del <key>      — delete a key
///   flush          — delete all `devoxx-chatbot:*` keys
///   quit           — exit

static final String KEY_PREFIX = "devoxx-chatbot:";

String env(String name, String defaultValue) {
  var value = System.getenv(name);
  return (value != null && !value.isBlank()) ? value : defaultValue;
}

JedisPooled connect() {
  var host = env("VALKEY_HOST", "localhost");
  var port = Integer.parseInt(env("VALKEY_PORT", "6379"));
  var user = env("VALKEY_USER", null);
  var password = env("VALKEY_PASSWORD", null);
  var ssl = user != null;

  IO.println("Connecting to %s:%d (TLS=%s, user=%s)".formatted(host, port, ssl, user));

  var config = DefaultJedisClientConfig.builder()
      .user(user)
      .password(password)
      .ssl(ssl)
      .build();
  return new JedisPooled(new HostAndPort(host, port), config);
}

void listKeys(JedisPooled jedis) {
  Set<String> keys = jedis.keys(KEY_PREFIX + "*");
  if (keys.isEmpty()) {
    IO.println("  (no keys found)");
    return;
  }
  IO.println("  Found %d key(s):".formatted(keys.size()));
  for (var key : keys) {
    var type = jedis.type(key);
    var ttl = jedis.ttl(key);
    var len = "string".equals(type) ? String.valueOf(jedis.strlen(key)) : "?";
    IO.println("  - %s  [type=%s, size=%s bytes, ttl=%s]".formatted(key, type, len, ttl == -1 ? "none" : ttl + "s"));
  }
}

void getKey(JedisPooled jedis, String key) {
  var value = jedis.get(key);
  if (value == null) {
    IO.println("  (key not found)");
    return;
  }
  IO.println("  Value (%d chars):".formatted(value.length()));
  // Pretty print: if it starts with [ or {, it's likely JSON
  if (value.startsWith("[") || value.startsWith("{")) {
    // Simple indented display — truncate if very long
    var display = value.length() > 2000 ? value.substring(0, 2000) + "\n  ... (truncated)" : value;
    IO.println("  %s".formatted(display));
  } else {
    IO.println("  %s".formatted(value));
  }
}

void deleteKey(JedisPooled jedis, String key) {
  var deleted = jedis.del(key);
  IO.println(deleted > 0 ? "  Deleted: %s".formatted(key) : "  (key not found)");
}

void flushKeys(JedisPooled jedis) {
  Set<String> keys = jedis.keys(KEY_PREFIX + "*");
  if (keys.isEmpty()) {
    IO.println("  (no keys to delete)");
    return;
  }
  for (var key : keys) {
    jedis.del(key);
    IO.println("  Deleted: %s".formatted(key));
  }
  IO.println("  Flushed %d key(s)".formatted(keys.size()));
}

void printHelp() {
  IO.println("""
        Commands:
          list           — list all keys matching devoxx-chatbot:*
          get <key>      — display the value of a key
          del <key>      — delete a key
          flush          — delete all devoxx-chatbot:* keys
          quit           — exit
      """);
}

void main() {
  var jedis = connect();

  // Verify connection by listing keys on startup
  IO.println("Connected!\n");
  listKeys(jedis);
  IO.println("");
  printHelp();

  var scanner = new Scanner(System.in);
  while (true) {
    IO.print("valkey> ");
    var line = scanner.nextLine().trim();
    if (line.isEmpty()) continue;

    var parts = line.split("\\s+", 2);
    var cmd = parts[0].toLowerCase();
    var arg = parts.length > 1 ? parts[1] : "";

    switch (cmd) {
      case "list" -> listKeys(jedis);
      case "get" -> {
        if (arg.isEmpty()) { IO.println("  Usage: get <key>"); }
        else { getKey(jedis, arg); }
      }
      case "del" -> {
        if (arg.isEmpty()) { IO.println("  Usage: del <key>"); }
        else { deleteKey(jedis, arg); }
      }
      case "flush" -> flushKeys(jedis);
      case "quit", "exit", "q" -> {
        IO.println("Bye!");
        return;
      }
      case "help", "?" -> printHelp();
      default -> IO.println("  Unknown command: %s (type 'help' for usage)".formatted(cmd));
    }
    IO.println("");
  }
}
