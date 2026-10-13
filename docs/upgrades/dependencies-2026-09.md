# Montée de versions des dépendances — septembre 2026

**Scope :** tous les `//DEPS` des scripts JBang de `chatbot/` et `fine-tuning/datasets/`
**Project :** `ai-learn` (branche `upgrade-dependencies`) · **Java release :** 25 (`//JAVA 25`), JDK 26 local · **Version managed by :** direct (`//DEPS`), sauf le script `_07_01`, dont les versions viennent du BOM `quarkus-bom`
**Verdict :** montée guidée. Une seule migration de code est obligatoire : `JedisPooled` → `RedisClient` pour jedis 8. Tout le reste se limite à changer les numéros de version. Seule piège : `langchain4j-community-mcp-server` s'arrête à `1.20.0-beta30`.

| Dépendance | Avant | Après | Versions intermédiaires lues |
|---|---|---|---|
| `dev.langchain4j` (stables : core, langchain4j, open-ai) | 1.12.2 | 1.20.2 | 1.13.0 → 1.20.1 (1.19.1 exclue, publiée par erreur) |
| `dev.langchain4j` (beta : agentic, mcp, pgvector, skills, tavily) | 1.12.2-beta22 | 1.20.2-beta30 | idem |
| `dev.langchain4j:langchain4j-community-mcp-server` | 1.12.2-beta22 | **1.20.0-beta30** (dernière publiée) | 1.13.0-beta23 → 1.19.0-beta29 |
| `io.quarkus.platform:quarkus-bom` | 3.32.1 | 3.40.1 | 3.32.2 → 3.40.0.CR1 |
| `io.quarkiverse.mcp:quarkus-mcp-server-http` | 1.10.2 | 2.0.1 | 1.10.3 → 2.0.0 |
| `redis.clients:jedis` | 6.0.0 | 8.0.1 | 6.1.x, 7.0 → 7.5.x, 8.0.0 |
| `ch.qos.logback:logback-classic` | 1.5.6 | 1.6.4 | 1.5.7 → 1.6.3 |
| `org.postgresql:postgresql` | 42.7.5 | 42.7.13 | 42.7.6 → 42.7.12 |
| `com.google.code.gson:gson` | 2.10.1 | 2.14.0 | 2.11.0 → 2.13.2 |

---

## Table 1 — Release differences

Seuls les changements visibles pour l'utilisateur sont listés. Les montées de dépendances transitives sont regroupées en une ligne par librairie.

### LangChain4j

| # | Change | Type | Module / artifact | What it means | Affects this project | Source |
|---|--------|------|-------------------|---------------|----------------------|--------|
| 1 | `langchain4j-community-mcp-server` n'est pas publié en 1.20.2-beta30 | 📦 Dependency | community-mcp-server | Il faut cibler `1.20.0-beta30`. Garder `1.12.2-beta22` n'est pas viable, car les classes du protocole MCP ont changé (#6117) | Yes — `_06_01:6` | [maven-metadata.xml](https://repo1.maven.org/maven2/dev/langchain4j/langchain4j-community-mcp-server/maven-metadata.xml), [release 1.20-beta30](https://github.com/langchain4j/langchain4j-community/releases/tag/1.20-beta30) |
| 2 | 1.19.1 / 1.19.1-beta29 publiées par erreur depuis `main` | ⚠️ Info | tous | À ne jamais utiliser comme cible | No | [release 1.19.1](https://github.com/langchain4j/langchain4j/releases/tag/1.19.1) |
| 3 | `HttpMcpTransport` (SSE legacy) supprimé | 🔴 Breaking | mcp | `StreamableHttpMcpTransport` reste disponible avec le même builder | No — le projet utilise `StreamableHttpMcpTransport` (`_07_00:52`) | [#5881](https://github.com/langchain4j/langchain4j/pull/5881), [1.19.0](https://github.com/langchain4j/langchain4j/releases/tag/1.19.0) |
| 4 | Le client MCP suit la spec 2026-07-28 : il envoie d'abord `server/discover`, puis revient à `initialize` si le serveur ne connaît pas cette méthode | 🔧 Improvement | mcp | Changement de comportement à l'exécution. `DefaultMcpClient.Builder.protocolVersion(String)` permet de fixer la version | Yes (runtime) — `_06_00:59`, `_07_00:60` | [#5881](https://github.com/langchain4j/langchain4j/pull/5881) |
| 5 | Les classes du protocole MCP n'exposent plus de types Jackson. `McpTransport.initialize` et d'autres méthodes passent `@Deprecated(forRemoval)` | 🔴 Breaking (API interne) | mcp | Seules les implémentations de transport ou de serveur sont concernées | Indirect — c'est la raison du point 1 | [#6117](https://github.com/langchain4j/langchain4j/pull/6117) |
| 6 | AI Services non bloquants : les méthodes peuvent retourner `CompletableFuture` ou `Flow.Publisher`. Des défauts asynchrones sont ajoutés sur `ChatMemoryStore` et `EmbeddingModel` | ✨ Feature 🧪 `@Experimental` | core, langchain4j | Changement additif. `TokenStream` n'est pas modifié | Optional | [#5527](https://github.com/langchain4j/langchain4j/pull/5527) |
| 7 | En streaming, `ChatResponse.tokenUsage()` vaut `null` si le fournisseur ne remonte pas l'usage | 🐛 Fix / behavior | langchain4j | Un appel `tokenUsage().inputTokenCount()` sans vérification peut lever une NPE | Yes — `ChatbotUtils.java:145` | [#6203](https://github.com/langchain4j/langchain4j/pull/6203) |
| 8 | `@Tool` hérités des superclasses et interfaces désormais découverts | 🔴 Breaking (listé comme tel) | core | Concerne uniquement les classes d'outils qui héritent d'une autre classe | No — `BearConfTools` et `BearConfSkillTools` n'héritent de rien | [#5328](https://github.com/langchain4j/langchain4j/pull/5328) |
| 9 | `@P` gagne les attributs `name`, `description` et `defaultValue` | ✨ Feature | core | Compatible au niveau source | Optional | [#4846](https://github.com/langchain4j/langchain4j/pull/4846), [#5146](https://github.com/langchain4j/langchain4j/pull/5146) |
| 10 | `OutputGuardrail.failureWithMessageRemoval` / `fatalWithMessageRemoval` | ✨ Feature | core | Retire le message fautif de la mémoire | Optional — `_10` n'a pas de mémoire | [#5155](https://github.com/langchain4j/langchain4j/pull/5155) |
| 11 | OpenAI : le champ `reasoning` (vLLM, OpenRouter) est accepté comme alias de `reasoning_content` | 🐛 Fix | open-ai | Le raisonnement des endpoints compatibles OpenAI est lu. Il n'est exposé qu'avec `returnThinking(true)` | Optional — `ChatbotUtils.java:129` (commenté) | [#5843](https://github.com/langchain4j/langchain4j/pull/5843) |
| 12 | OpenAI streaming : les tool calls ne sont plus perdus, et les chunks `tool_calls` vides ou nuls sont gérés | 🐛 Fix | open-ai | Appels d'outils en streaming plus fiables | Yes (transparent) — `_05`, `_07_00`, `_11`, `_12` | #4890, #5209, #5712 |
| 13 | Skills : outils propres à un skill et `DefaultFileSystemSkill.toBuilder()` ; issue #4779 fermée | ✨ Feature 🧪 `@Experimental` | skills | Le contournement commenté dans `_11` peut maintenant être appliqué | Optional — `_11:54-59,85` | [#4732](https://github.com/langchain4j/langchain4j/pull/4732), [#5272](https://github.com/langchain4j/langchain4j/pull/5272) |
| 14 | `StdioMcpServerTransport.awaitClose()` | ✨ Feature | community-mcp-server | Peut remplacer `Thread.currentThread().join()` | Optional — `_06_01:154,159` | [community #605](https://github.com/langchain4j/langchain4j-community/pull/605) |
| 15 | Agentic : nouveaux patterns (voting, blackboard, debate), human-in-the-loop, compensation, corrections de thread-safety sur `AgentMonitor`. `AgentResponse` gagne `chatRequest` et `chatResponse` | ✨ Feature / 🐛 Fix | agentic | `agentName()` et `output()` ne changent pas | No (optionnel) | #4827, #5158, #5243, #5730, #5823 |
| 16 | PgVector : injection SQL corrigée dans les filtres de métadonnées, plus des corrections sur `isNotIn` et `ContainsString` | 🐛 Fix (sécurité) | pgvector | Aucun changement d'API | Yes (transparent) — `_05`, `_08` | commit 13a0698 (1.16.3), #5595 |
| 17 | Tavily passe de Retrofit/OkHttp à l'abstraction `HttpClient` | 📦 Dependency | tavily | Transparent : `builder().apiKey()` ne change pas | No | [#5772](https://github.com/langchain4j/langchain4j/pull/5772) |
| 18 | Dépendances : Jackson 2.21.1 → 2.22.1, slf4j 2.0.18, ajout de `mutiny-zero` et de `langchain4j-reactive-streaming` (beta) comme dépendances d'open-ai. Minimum Java inchangé (17) | 📦 Dependency | core, open-ai | — | No | diff des POMs 1.12.2 / 1.20.2 |

### Quarkus 3.32.1 → 3.40.1 et quarkus-mcp-server 1.10.2 → 2.0.1

| # | Change | Type | Module / artifact | What it means | Affects this project | Source |
|---|--------|------|-------------------|---------------|----------------------|--------|
| 19 | Le Quarkus Platform 3.40.1 aligne `quarkus-mcp-server` sur 2.0.1 | 📦 Dependency | platform | Cette combinaison est certifiée par la plateforme | Yes (positif) | `quarkus-mcp-server-bom-3.40.1.pom` |
| 20 | mcp-server 2.0 : l'artefact `-sse` relocalisé et le fallback de config `sse.*` sont supprimés, le batching JSON-RPC est retiré, `McpRequest.protocolVersion()` est typé | 🔴 Breaking | mcp-server | — | No — le projet utilise `-http` et aucune propriété `sse.*` | release-notes 2.0, #826, #723 |
| 21 | mcp-server 2.0 : protocole stateless 2026-07-28 ; les résultats contiennent `resultType` et `_meta` | ✨ Feature | mcp-server | Les clients stateful continuent de fonctionner | No action | #824, PR #937 |
| 22 | `@Tool` et `@ToolArg` (quarkiverse) ne sont pas dépréciés en 2.0.1 ; les annotations Java MCP sont supportées en plus | ✨ Feature | mcp-server | — | No | sources `Tool.java`, `ToolArg.java` 2.0.1 |
| 23 | `traffic-logging.enabled` et le root path `/mcp` sont inchangés | 🔧 Config | mcp-server | — | No | `McpServerRuntimeConfig.java:26`, doc http 2.0.1 |
| 24 | Guides de migration Quarkus 3.33 à 3.40 : rien sur core, arc ou vertx-http pour cette application. Depuis 3.38, le header `Host` est validé si `quarkus.http.host=localhost` | 🔧 Info | quarkus | `quarkus.http.host` n'est pas défini | No | wiki Migration-Guide-3.33 … 3.40 |
| 25 | Le BOM Quarkus 3.40.1 impose Jackson 2.21.7 alors que LangChain4j 1.20.2 déclare 2.22.1 | 📦 Dependency | BOM | Rétrograde une version mineure de Jackson pour LangChain4j. Ce décalage existait déjà dans la version d'origine. Le démarrage a été testé sans erreur, le chemin pgvector n'a pas été exécuté | Low risk — `_07_01` | `quarkus-bom-3.40.1.pom`, `jbang info classpath` |

### JDK 26 (environnement d'exécution, hors montée de versions)

| # | Change | Type | Module / artifact | What it means | Affects this project | Source |
|---|--------|------|-------------------|---------------|----------------------|--------|
| 25b | Le timeout de `HttpRequest.Builder::timeout` couvre désormais aussi la lecture du corps de la réponse, alors qu'il s'arrêtait à la réception des headers | 🔴 Breaking (runtime) | JDK `java.net.http` | `OpenAiStreamingChatModel` applique `timeout` (60 s par défaut, identique en 1.12.2 et 1.20.2) comme timeout de requête. Un stream de plus de 60 s est donc coupé avec `HttpTimeoutException: request timed out` | Yes — observé sur `_08` (génération de l'agenda), concerne tous les modèles streaming | [JDK-8208693](https://bugs.openjdk.org/browse/JDK-8208693), [Inside.java — HTTP Client Updates in Java 26](https://inside.java/2026/03/04/jdk-26-http-client/) ; `JdkHttpClient.java:123,180` (1.12.2 et 1.20.2) |

### jedis 6.0.0 → 8.0.1

| # | Change | Type | Module / artifact | What it means | Affects this project | Source |
|---|--------|------|-------------------|---------------|----------------------|--------|
| 26 | `JedisPooled` supprimé en 8.0.0, remplacé par `RedisClient` (introduit en 7.2.0) | 🔴 Breaking | jedis | Erreur de compilation | Yes — `ValkeyChatMemoryStore`, `ValkeyExplorer` | [v8.0.0](https://github.com/redis/jedis/releases/tag/v8.0.0) (#4488), `docs/migration-guides/v7-to-v8.md` ; absent du jar 8.0.1 (vérifié) |
| 27 | `DefaultJedisClientConfig.Builder.ssl(boolean)` déprécié au profit de `sslOptions(SslOptions)` | ⚠️ Deprecation | jedis | Warning `[deprecation]` à la compilation | Yes — 2 appels | v7.5.0 (#4497), `DefaultJedisClientConfig.java:394` |
| 28 | Vérification du hostname TLS activée par défaut | 🔧 Behavior / sécurité | jedis | Le handshake échoue si le CN ou le SAN du certificat ne correspond pas à `VALKEY_HOST` | Yes, en TLS (`VALKEY_USER` défini) | v8.0.0 (#4495), v7-to-v8.md |
| 29 | RESP3 négocié par défaut (`HELLO 3`, retour à RESP2 sinon) | 🔧 Behavior | jedis | Les commandes utilisées (`get`, `set`, `del`, `keys`, `type`, `ttl`, `strlen`) ne sont pas concernées | Low | v8.0.0 (#4468) |
| 30 | Classes de sharding, `PipelineBase` et `JedisSentineled` supprimés ; commons-pool2 2.13.1 ; Java minimum reste 8 | 🔴 Breaking / 📦 | jedis | — | No | v6-to-v7.md, v7-to-v8.md |

### Libs secondaires

| # | Change | Type | Module / artifact | What it means | Affects this project | Source |
|---|--------|------|-------------------|---------------|----------------------|--------|
| 31 | logback 1.6.x remplace 1.5.x directement, sauf les conditions Janino `<if>`, qui sont supprimées | 🔴 Breaking (limité) | logback | Requiert slf4j 2.0.x ; slf4j 2.0.19 est résolu | No — aucune config avec conditions | [news.html](https://logback.qos.ch/news.html) |
| 32 | logback : CVE-2024-12798, CVE-2024-12801, CVE-2025-11226 et CVE-2026-1225, puis d'autres jusqu'à la 1.6.3 | 🔒 Security | logback | Concerne surtout le chargement de fichiers de config | Faible exposition, mais c'est une raison de monter | news.html |
| 33 | pgjdbc : la version fixée à 42.7.5 est plus ancienne que celle déclarée par pgvector (42.7.7) | 📦 Dependency | postgresql | Cette version fixe rétrograde le driver | Yes | `langchain4j-pgvector-1.12.2-beta22.pom` |
| 34 | pgjdbc : CVE-2025-49146 (42.7.7), CVE-2026-42198 (DoS SCRAM, 42.7.11), CVE-2026-54291 (42.7.12, renforcé en 42.7.13) | 🔒 Security | postgresql | CVE-2026-42198 s'applique à une connexion SCRAM vers un pgvector distant | Yes (en principe) | release notes REL42.7.7 / 42.7.11 / 42.7.12 / 42.7.13 |
| 35 | gson 2.11 → 2.14 : `TypeToken` refuse les variables de type, les types de collection abstraits lèvent une exception, adaptateurs `java.time` ajoutés, détection des clés dupliquées corrigée | 🔴 Breaking (limité) / ✨ | gson | Tous les `TypeToken` du projet utilisent des types concrets | No — sorties des datasets identiques octet par octet | release notes gson 2.11.0 → 2.14.0 |

---

## Table 2 — Code changes required

| # | Criticality | Location | Current code | Change to make | Effort | Why / source |
|---|-------------|----------|--------------|----------------|--------|--------------|
| 1 | 🔴 Blocking | `chatbot/ValkeyChatMemoryStore.java:8,24,37` | `new JedisPooled(new HostAndPort(host, port), config)` | `RedisClient.builder().hostAndPort(new HostAndPort(host, port)).clientConfig(config.build()).build()` | S | Table 1 #26 |
| 2 | 🔴 Blocking | `chatbot/ValkeyExplorer.java:8,31,45,48,63,80,85` | `JedisPooled` (import, retour, paramètres) | `RedisClient` | S | Table 1 #26 |
| 3 | 🔴 Blocking | `chatbot/_06_01_BearConfMcpServer.java:6` | `langchain4j-community-mcp-server:1.12.2-beta22` | `:1.20.0-beta30` | S | Table 1 #1 |
| 4 | 🟠 High | `ValkeyChatMemoryStore.java:31`, `ValkeyExplorer.java:43` | `.ssl(ssl)` | `if (ssl) { config.sslOptions(SslOptions.defaults()); }` (truststore de la JVM, `SslVerifyMode.FULL`) | S | Table 1 #27 |
| 5 | 🟡 Medium | `//DEPS` de tous les scripts | versions de départ | versions cibles (tableau d'en-tête) | S | Maven Central |
| 5b | 🔴 Blocking (runtime, JDK 26) | builders `OpenAiStreamingChatModel` de `_01` à `_08`, `_10`, `_11`, `_12` | pas de `.timeout(...)`, donc 60 s par défaut | `.timeout(Duration.ofMinutes(5))` après `.baseUrl(...)`, comme dans `_09` | S | Table 1 #25b |
| 6 | 🟢 Low | `chatbot/ChatbotUtils.java:145-151` | `response.tokenUsage().inputTokenCount()` | vérification de nullité sur `tokenUsage()` et ses compteurs | S | Table 1 #7 |
| 7 | 🟠 High (🧪) — lève les deux ⚠️ laissés dans le code | `chatbot/_11_SkillChatbot.java:54-59,85` | `Skills.from(fileSystemSkill)` + `new BearConfSkillTools()` passé à l'AI Service | `((DefaultFileSystemSkill) fileSystemSkill).toBuilder().tools(new BearConfSkillTools()).build()`. Le cast est nécessaire parce que `loadSkill()` retourne l'interface. Le commentaire actuel (`.tool(new BearConfSkillTools(talks))`) ne correspond à aucune API : la méthode est `tools(Object...)` et le constructeur ne prend pas d'argument | S | Table 1 #13 ; module `@Experimental` |
| 8 | 🟢 Low (optionnel) | `chatbot/_06_01_BearConfMcpServer.java:154,159` | `Thread.currentThread().join()` | `transport.awaitClose()` | S | Table 1 #14 |
| 9 | 🟢 Low (optionnel) | `ChatbotUtils.java:129` + builders `OpenAiStreamingChatModel` | `//.onPartialThinking(IO::println)` | `.returnThinking(true)` sur le builder, puis décommenter la ligne | S | Table 1 #11 |

Les lignes 1 à 7 (et 5b) sont appliquées. Les lignes 8 et 9 ne le sont pas : ce sont des choix de démo, à décider.

---

## Upgrade plan

Plusieurs étapes modifient les mêmes lignes `//DEPS` dans les mêmes fichiers. Les changements ont donc été appliqués en une seule passe. Pour committer par étape, il faudrait passer par `git add -p`. Un commit unique est proposé à la fin.

### Step 1 — Rendre le script de build exhaustif
**Changes :** `bin/check-all-builds.sh` (fichier non suivi par git). `_03_MemoryChatbot.java`, qui n'existe plus, est remplacé par `_03_00` et `_03_01`, et `_10`, `_11`, `_12` sont ajoutés.
**Validate :** `./bin/check-all-builds.sh`
**Expected :** 17 scripts OK avec les versions d'origine (vérifié)

### Step 2 — jedis 6.0.0 → 8.0.1
**Changes :** `ValkeyChatMemoryStore.java`, `ValkeyExplorer.java` (Table 2 #1, #2, #4), `//DEPS` jedis dans 13 scripts
**Validate :** `./bin/check-all-builds.sh`, puis `jbang chatbot/ValkeyExplorer.java` contre le Valkey managé
**Expected :** compilation OK. À l'exécution, vérifier le handshake TLS (hostname)
```
build(deps): 📦 bump jedis from 6.0.0 to 8.0.1 and migrate to RedisClient
```

### Step 3 — LangChain4j 1.12.2 → 1.20.2 / 1.20.2-beta30
**Changes :** `//DEPS dev.langchain4j:*` de tous les scripts ; community-mcp-server en `1.20.0-beta30` ; `ChatbotUtils.java` (Table 2 #6)
**Validate :** `./bin/check-all-builds.sh`, puis `./bin/check-all-runs.sh 06 07 08 09 11` pour MCP, agentic et skills
**Expected :** compilation OK sans warning de dépréciation (vérifié avec `-Xlint:deprecation -Xlint:removal`)
```
build(deps): 📦 bump langchain4j from 1.12.2 to 1.20.2
```

### Step 4 — Quarkus 3.32.1 → 3.40.1, quarkus-mcp-server 1.10.2 → 2.0.1
**Changes :** `_07_01_BearConfMcpQuarkusServer.java:5-6`
**Validate :** `jbang chatbot/_07_01_BearConfMcpQuarkusServer.java`, puis `_07_00` connecté dessus
**Expected :** démarrage OK et `/mcp` expose 4 tools (vérifié par curl sur une copie)
```
build(deps): 📦 bump quarkus to 3.40.1 and quarkus-mcp-server to 2.0.1
```

### Step 5 — Libs secondaires
**Changes :** logback 1.6.4, postgresql 42.7.13, gson 2.14.0 dans tous les `//DEPS`
**Validate :** `./bin/check-all-builds.sh` ; `jbang fine-tuning/datasets/BearConfDatasetCreation.java`, puis `git diff --stat fine-tuning/datasets`
**Expected :** aucune différence dans les datasets générés
```
build(deps): 📦 bump logback to 1.6.4, pgjdbc to 42.7.13 and gson to 2.14.0
```

### Commit unique proposé (tout le lot)
```
build(deps): 📦 upgrade langchain4j 1.20.2, quarkus 3.40.1, jedis 8.0.1 and libs

- langchain4j 1.12.2 -> 1.20.2 / 1.20.2-beta30
  (community-mcp-server 1.20.0-beta30, latest published)
- quarkus-bom 3.32.1 -> 3.40.1, quarkus-mcp-server-http 1.10.2 -> 2.0.1
- jedis 6.0.0 -> 8.0.1: JedisPooled replaced by RedisClient,
  ssl(boolean) replaced by sslOptions(SslOptions.defaults())
- logback 1.6.4, postgresql 42.7.13, gson 2.14.0
- null-guard tokenUsage() in ChatbotUtils (null since langchain4j 1.20.0)
- set a 5 min timeout on streaming models: since JDK 26 the HttpClient
  request timeout also covers the response body (JDK-8208693)
- check-all-builds.sh covers every chatbot script
```

**Rollback :** revert du commit. Aucune donnée persistée ne change de format : Valkey stocke le même JSON sous les mêmes clés.
**À surveiller à l'exécution (non couvert par la compilation) :**
- handshake TLS de jedis vers le Valkey managé ;
- détection du protocole MCP entre le client 1.20.2 et le serveur community (`_06`) ou Quarkus (`_07`) ;
- chemins pgvector avec Jackson 2.21.7 dans `_07_01`.

---

## Sources & coverage

**Consulted :**
- GitHub Releases de LangChain4j, 1.13.0 → 1.20.2, y compris 1.19.1, 1.19.2 et 1.19.3 ;
- releases de langchain4j-community, 1.13.0-beta23 → 1.20-beta30 ;
- sources JARs 1.12.2 et 1.20.2 (core, langchain4j, open-ai) et 1.12.2-beta22 et 1.20.2-beta30 (agentic, mcp, pgvector, skills, tavily), community-mcp-server 1.12.2-beta22 et 1.20.0-beta30 ;
- `revapi.json` de LangChain4j au tag 1.20.2 ;
- releases de quarkus-mcp-server 1.10.2 → 2.0.1 et `release-notes.adoc` ;
- wiki Quarkus Migration-Guide-3.33 → 3.40 ;
- guides jedis `v6-to-v7.md` et `v7-to-v8.md`, releases 6.1 → 8.0.1, sources JAR 8.0.1 ;
- news de logback ;
- release notes de pgjdbc 42.7.6 → 42.7.13 et de gson 2.11 → 2.14.

**Verified locally :**
- `./bin/check-all-builds.sh` : 17/17 OK avant et après ;
- builds d'essai avec `-Xlint:deprecation`, sans aucun warning ;
- démarrage de `_07_01` et appels curl sur `/mcp` (initialize, tools/list, tools/call) sur une copie ;
- sorties des datasets gson identiques entre 2.10.1 et 2.14.0 ;
- absence de `JedisPooled` dans le jar 8.0.1 ;
- dernière version de community-mcp-server sur Maven Central.

**Not available :**
- pas de CHANGELOG.md chez LangChain4j (les GitHub Releases sont la seule source) ;
- notes vides pour 1.16.2, 1.17.2 et 1.18.1 ;
- le parent POM de LangChain4j n'a pas été trouvé ; le minimum Java 17 a été déduit du bytecode et du manifest.

**Unverified / open questions :**
- aucun chatbot exécuté de bout en bout (pas de LLM, pgvector, Valkey ni Tavily). Le comportement à l'exécution est déduit des sources ;
- incompatibilité de community-mcp-server 1.12.2-beta22 avec mcp 1.20.x : déduite des sources, pas testée ;
- vérification du hostname TLS de jedis 8 contre le certificat du Valkey managé ;
- `bin/check-all-runs.sh` référence encore `_03_MemoryChatbot.java`, qui n'existe plus. Fichier non modifié.
