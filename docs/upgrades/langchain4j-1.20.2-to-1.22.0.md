# LangChain4j 1.20.2 → 1.22.0

**Scope:** `langchain4j`, `langchain4j-open-ai` (stable) · `langchain4j-agentic`, `langchain4j-mcp`, `langchain4j-skills`, `langchain4j-pgvector`, `langchain4j-web-search-engine-tavily` (beta) · `langchain4j-community-mcp-server`
**Intermediate releases included:** 1.21.0 / 1.21.0-beta31 (main), 1.21.0-beta31 (community)
**Project:** `ai-learn` (`chatbot/`, `fine-tuning/datasets/`), JBang scripts, `//JAVA 25`, local runtime JDK 26 · **Version managed by:** direct `//DEPS`
**Verdict:** Drop-in bump. Aucune API utilisée n'est cassée. Une seule différence visible : un WARN de 17 lignes au démarrage de chaque script avec outils, levé en déclarant explicitement les handlers d'erreurs d'outils.

| Artifact | Avant | Après |
|---|---|---|
| `langchain4j`, `langchain4j-open-ai` | 1.20.2 | 1.22.0 |
| `langchain4j-agentic`, `-mcp`, `-skills`, `-pgvector`, `-web-search-engine-tavily` | 1.20.2-beta30 | 1.22.0-beta32 |
| `langchain4j-community-mcp-server` | 1.20.0-beta30 | 1.22.0-beta32 |

La ligne community a rattrapé la ligne principale : les deux sont en `-beta32`.

## Table 1 — Release differences

| # | Change | Type | Module / artifact | What it means | Affects this project | Source |
|---|--------|------|-------------------|---------------|----------------------|--------|
| 1 | `ToolErrorHandlingNotice` : WARN une fois par JVM quand un AI Service a des outils sans handlers d'erreurs explicites et au moins une méthode bloquante. `TokenStream` compte comme bloquant. Seuls `CompletableFuture`/`CompletionStage`/`Flow.Publisher` sont exemptés. | 🔧 Improvement | langchain4j | 17 lignes de WARN au démarrage de chaque démo avec outils. Les défauts actuels changeront dans une prochaine version. | Oui — 7 builders (`_05`, `_06_00`, `_07_00`, `_08`, `_09`, `_11`, `_12`), vérifié à l'exécution | [#6420](https://github.com/langchain4j/langchain4j/pull/6420), `DefaultAiServices#hasBlockingMethod` |
| 2 | Handlers prêts à l'emploi : `ToolExecutionErrorHandler.failInvocation()`, `sendExceptionMessageToLlm()`, `failInvocationUnlessVisibleToLlm()`, `sendGenericMessageToLlmUnlessVisibleToLlm(String)`, ainsi que `ToolArgumentsErrorHandler.failInvocation()` et `sendExceptionMessageToLlm()`. Nouvelle interface `ToolErrorVisibleToLlm`. | ✨ Feature | langchain4j, langchain4j-core | Une exception décide elle-même ce que le LLM voit. Les défauts pour les méthodes bloquantes sont inchangés en 1.22.0. | Oui — adopté (Table 2 #1) | [#6420](https://github.com/langchain4j/langchain4j/pull/6420), `ToolExecutionErrorHandler.java` |
| 3 | Erreur applicative MCP (`isError: true`) levée en `McpApplicationErrorException`, qui implémente `ToolErrorVisibleToLlm`. | 🔧 Improvement | langchain4j-mcp | Avec `failInvocationUnlessVisibleToLlm()`, une erreur renvoyée par un serveur MCP continue d'arriver au LLM. | Oui — `_06_00`, `_07_00` | `McpApplicationErrorException.java` |
| 4 | Les erreurs des outils de skill (`activate_skill`, `read_skill_resource`) sont levées en `LlmVisibleToolExecutionException`. | 🔧 Improvement | langchain4j-skills | Avec le nouveau handler, une erreur de skill (ressource introuvable…) reste visible du LLM au lieu de faire échouer l'appel. | Oui — `_11` | `AbstractSkillToolExecutor.java` (diff 1.20.2-beta30 → 1.22.0-beta32) |
| 5 | `SystemMessage` : builder, `attributes()` (`@Experimental`), sérialisation JSON via le builder. | 🔧 Improvement | langchain4j-core | Le format stocké reste lisible : une mémoire écrite en 1.20 se relit en 1.22 (vérifié). | Oui — `FileChatMemoryStore`, `ValkeyChatMemoryStore` | `JacksonChatMessageJsonCodec.java`, `SystemMessage.java` |
| 6 | Supervisor : summarizer de contexte créé par agent au lieu d'un singleton statique. Cartes d'agents rendues avec les types génériques. Erreur explicite si la réponse du planner n'a pas de nom d'agent. | 🐛 Fix | langchain4j-agentic | Pas de changement d'usage. `.context(...)` (`AgenticScopeContextGenerator`) est inchangé. | Oui — `_08`, `_09`, sans code à changer | [#6247](https://github.com/langchain4j/langchain4j/pull/6247), [#6392](https://github.com/langchain4j/langchain4j/pull/6392), [#6528](https://github.com/langchain4j/langchain4j/pull/6528) |
| 7 | Agents non-IA programmatiques : `AgenticServices.nonAiAgentBuilder(...)`. | ✨ Feature | langchain4j-agentic | Permet de déclarer une étape Java pure dans un workflow sans interface annotée. | Optionnel | [#6496](https://github.com/langchain4j/langchain4j/pull/6496) |
| 8 | Decision Models : `DecisionModel`, Decision Services, `DecisionRouterPlanner`, `OpenAiDecisionModel` (API `/decisions`). | 🧪 Experimental | langchain4j-core, langchain4j, langchain4j-agentic, langchain4j-open-ai | Nécessite un fournisseur qui implémente le protocole (TypeSafe, OpenRouter, OpenAI Decisions). | Non — non disponible sur OVHcloud AI Endpoints (non vérifié côté fournisseur) | [#6469](https://github.com/langchain4j/langchain4j/pull/6469), [#6561](https://github.com/langchain4j/langchain4j/pull/6561), [#6611](https://github.com/langchain4j/langchain4j/pull/6611) |
| 9 | `StdioMcpTransport.Builder.workingDirectory(Path)` | ✨ Feature | langchain4j-mcp | Lance le serveur MCP stdio dans un répertoire donné. | Optionnel — `_06_00` dépend aujourd'hui du `cd chatbot` de `bin/06-*.sh` | [#6487](https://github.com/langchain4j/langchain4j/pull/6487) |
| 10 | Prompt caching OpenAI (gpt-5.6+) : `OpenAiPromptCacheOptions`, `OpenAiPromptCacheBreakpoint`. | ✨ Feature | langchain4j-open-ai | Uniquement sur demande explicite. | Non | [#6272](https://github.com/langchain4j/langchain4j/pull/6272) |
| 11 | Correctifs sur des chemins utilisés : streaming OpenAI (index d'appel d'outil négatif, corrélation d'appels d'outils), résolution d'outil par nom de `ToolSpecification`, `TokenUsage.add()` récursion infinie, pgvector (fermeture de connexion en échec, parsing locale-indépendant), MCP (interruption, pas de redémarrage après `close()`). | 🐛 Fix | divers | Plus robuste, pas de changement d'usage. | Oui, transparent | [#6254](https://github.com/langchain4j/langchain4j/pull/6254), [#6421](https://github.com/langchain4j/langchain4j/pull/6421), [#6268](https://github.com/langchain4j/langchain4j/pull/6268), [#6322](https://github.com/langchain4j/langchain4j/pull/6322), [#6324](https://github.com/langchain4j/langchain4j/pull/6324), [#6444](https://github.com/langchain4j/langchain4j/pull/6444), [#6362](https://github.com/langchain4j/langchain4j/pull/6362), [#6427](https://github.com/langchain4j/langchain4j/pull/6427) |
| 12 | `langchain4j-community-mcp-server`, `langchain4j-http-client(-jdk)`, `langchain4j-web-search-engine-tavily` : sources identiques entre les deux versions. | 📦 Dependency/build | — | Bump sans effet. | — | diff des sources JAR |
| 13 | `SupervisorPlanner` : constructeur passé de `public` à package-private. | 🔴 Breaking (beta) | langchain4j-agentic | Ne casse que du code qui instancie le planner à la main. | Non — instancié par le builder | diff `SupervisorPlanner.java` |

Aucun `@Deprecated` ajouté dans les modules utilisés. Les autres changements (Bedrock, Anthropic, Gemini, A2A, Milvus, Elasticsearch, jitLLM, bumps de dépendances de la doc) concernent des modules absents du projet.

## Table 2 — Code changes required

| # | Criticality | Location | Current code | Change to make | Effort | Why / source |
|---|-------------|----------|--------------|----------------|--------|--------------|
| 1 | 🟠 High | `_05_ToolChatbot.java`, `_06_00_McpChatbot.java`, `_07_00_McpHttpChatbot.java`, `_08_AgenticChatbot.java` (agent `TalkSearcher`), `_09_SupervisorChatbot.java` (agent `TalkSearcher`), `_11_SkillChatbot.java`, `_12_WebSearchChatbot.java` | `.tools(...)` sans handler | `.toolArgumentsErrorHandler(ToolArgumentsErrorHandler.sendExceptionMessageToLlm())` et `.toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocationUnlessVisibleToLlm())` | S | Supprime le WARN (vérifié à l'exécution) et adopte dès maintenant les futurs défauts annoncés par `ToolErrorHandlingNotice`. Changement de comportement : une exception d'un outil Java qui n'implémente pas `ToolErrorVisibleToLlm` fait désormais échouer l'appel au lieu d'être envoyée au LLM. Aucun outil du projet ne lève d'exception aujourd'hui. Les erreurs MCP et skills restent visibles du LLM (Table 1 #3, #4). |
| 2 | 🟢 Low | `_06_00_McpChatbot.java:53` | `.command(List.of("jbang", "_06_01_BearConfMcpServer.java"))` | ajouter `.workingDirectory(...)` | S | Optionnel : rendrait `_06_00` lançable hors de `chatbot/`. Non appliqué. |

**Contournements en attente :** aucun trouvé. Seuls du code commenté de démo (`beforeToolExecution`, `LocalDateTime.now()`) et des bascules entre variantes (mémoire, store) sont présents, sans lien avec une issue LangChain4j.

Les lignes 1 et la montée de version sont appliquées. La ligne 2 ne l'est pas.

## Upgrade plan

### Step 1 — Bump LangChain4j
**Changes:** `//DEPS` de tous les scripts `chatbot/_*.java` et `fine-tuning/datasets/*.java`
**Validate:** `./bin/check-all-builds.sh`
**Expected:** 17/17 OK, une fois terminé le renommage `WHERE_IS_BEARCONF` → `WHAT_IS_BEARCONF` en cours dans le working tree (sans lien avec LangChain4j : 4 scripts l'utilisent encore)
**Commit message:**
```
build(deps): 📦 upgrade langchain4j to 1.22.0 / 1.22.0-beta32
```

### Step 2 — Explicit tool error handlers
**Changes:** les 7 builders de la Table 2 #1 (plus les deux imports)
**Validate:** `./bin/check-all-builds.sh`, puis lancer `_05` : plus de WARN `ToolErrorHandlingNotice` au démarrage
**Commit message:**
```
fix: 🧯 declare tool error handlers explicitly (langchain4j 1.21 notice)
```

Les deux étapes peuvent tenir en un seul commit si l'on préfère que la montée de version n'affiche jamais le WARN.

**Rollback :** revenir aux `//DEPS` 1.20.2 / 1.20.2-beta30 / 1.20.0-beta30 et retirer les deux lignes de handlers (elles n'existent pas en 1.20.2).
**À surveiller en démo :** si un outil Java lève une exception (bug, base injoignable), l'appel échoue désormais avec une stack trace au lieu d'une réponse du LLM qui contourne l'erreur.

## Sources & coverage

**Consulted:** GitHub Releases 1.21.0 et 1.22.0 (langchain4j), 1.21.0-beta31 et 1.22.0-beta32 (langchain4j-community) · `maven-metadata.xml` de chaque artifact · sources JAR 1.20.2(-beta30) et 1.22.0(-beta32) de `langchain4j-core`, `langchain4j`, `langchain4j-open-ai`, `langchain4j-http-client(-jdk)`, `langchain4j-agentic`, `langchain4j-mcp`, `langchain4j-skills`, `langchain4j-pgvector`, `langchain4j-web-search-engine-tavily`, `langchain4j-community-mcp-server` (1.20.0-beta30 → 1.22.0-beta32), avec un diff complet par module.
**Vérifié à l'exécution (JDK 26, sans LLM) :** relecture et aller-retour de `chatbot/chat-memory.json` (écrit en 1.20) avec `ChatMessageDeserializer` 1.22.0 ; WARN `ToolErrorHandlingNotice` émis pour un AI Service `TokenStream` avec outils, absent avec les handlers explicites ; compilation des 17 scripts (dont 4 sur une copie avec le renommage d'enum complété).
**Non vérifié :**
- Exécution des démos avec le LLM : `_08`, `_09` (supervisor), `_06`/`_07` (MCP) et `_11` (skills) après le bump.
- Relecture des mémoires stockées dans Valkey : même codec que le fichier, donc même résultat attendu, mais non testé contre le serveur.
- Disponibilité de l'API Decisions sur OVHcloud AI Endpoints.
