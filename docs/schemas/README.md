# Schémas ReAct et workflow BearConf : conventions

Schémas pour un talk sur la boucle agentique ReAct, illustrée avec le use case BearConf (`ai-learn/chatbot/_08_AgenticChatbot.java` et `_09_SupervisorChatbot.java`, LangChain4j agentic).

## Fichiers

| Fichier | Contenu |
|---|---|
| `react-loop-slide` | Principe de la boucle ReAct : Raisonner → Agir → Observer, sortie vers la réponse finale |
| `react-loop-build-1/2/3` | Exemple déroulé (parapluie à Tours), révélé un tour à la fois |
| `bearconf-workflow` | Vue d'ensemble : boucle qualité (TalkSearcher + QualityScorer) puis AgendaPlanner |
| `bearconf-talksearcher-react` | La boucle ReAct à l'intérieur de TalkSearcher |
| `lora-finetuning` | Fine-tuning LoRA de Llama 3.2 3B (Axolotl) : poids gelés (W, carré) + adaptateur entraînable en bypass (A et B, barres fines, à l'échelle), puis merge |
| `aiendpoints-1` à `-6` | Série AI Endpoints : chatbot, system prompt, mémoire, RAG, MCP, function calling. Même ossature d'une slide à l'autre (utilisateur à gauche, application au centre, LLM à droite, tools au-dessus, mémoire/RAG en dessous) pour un effet de build |
| `aideploy-1` à `-5` | Même série que AI Endpoints (chatbot, system prompt, mémoire, RAG, tools), avec le LLM servi par vLLM sur AI Deploy ; les embeddings restent sur AI Endpoints |
| `deploy-1-vllm` | Déploiement de Llama 3.2 3B depuis Hugging Face avec vLLM sur AI Deploy |
| `deploy-2-finetune` | Dataset Creator → fine-tuning Axolotl sur AI Training → Hugging Face → vLLM sur AI Deploy, métriques dans W&B |
| `bearconf-supervisor` | Mode superviseur (`_09_SupervisorChatbot.java`) : un agent LLM superviseur choisit quel agent appeler |

Chaque schéma existe en SVG (source à modifier) et en PNG (à insérer dans les slides).

## Format

- Toile de travail 680×425 (16:10), puis recadrage au contenu avec `crop.py` : marge de 12 unités, cadre commun pour tous les schémas d'une même série (positions identiques d'une slide à l'autre pour l'effet de build).
- Les schémas sont prévus pour une zone de contenu sous un titre de slide : insérer les images d'une même série à la même taille et à la même position.
- Chaîne de génération, dans cet ordre (le recadrage mesure le contenu sur les PNG) : `build.py` → `render.py` → `crop.py` → `render.py`.
- PNG à fond transparent ; le SVG garde un fond blanc (`<rect width="680" height="425" fill="#FFFFFF"/>`), retiré au rendu.
- Pas de titre dans le schéma : il est porté par le titre natif de la slide.
- Contenu centré verticalement dans la slide.
- Police : Inter, avec repli Segoe UI, Helvetica, Arial ; code en JetBrains Mono / Consolas.

## Style

- Boîtes arrondies (`rx="8"`), bordure fine 0.6, flèches grises `#73726C`.
- Emoji au début de chaque titre de boîte (🙋 🧠 🛠️ 👀 ✅ 📚 🔁 🤔).
- Une couleur par rôle (fond / bordure / titre / sous-titre) :

| Rôle | Fond | Bordure | Titre | Sous-titre |
|---|---|---|---|---|
| Raisonner, TalkSearcher (violet) | `#EEEDFE` | `#534AB7` | `#3C3489` | `#534AB7` |
| Agir (vert d'eau) | `#E1F5EE` | `#0F6E56` | `#085041` | `#0F6E56` |
| Observer, QualityScorer, adaptateur LoRA entraînable (corail) | `#FAECE7` | `#993C1D` | `#712B13` | `#993C1D` |
| Réponse finale, AgendaPlanner (vert) | `#EAF3DE` | `#3B6D11` | `#27500A` | `#3B6D11` |
| Poids gelés, modèle de base (bleu) | `#E6F1FB` | `#185FA5` | `#0C447C` | `#185FA5` |
| Application Java / LangChain4j, Dataset Creator, ingestion (vert d'eau) | `#E1F5EE` | `#0F6E56` | `#085041` | `#0F6E56` |
| Services OVHcloud : AI Endpoints, PostgreSQL, AI Training, AI Deploy (bleu) | `#E6F1FB` | `#185FA5` | `#0C447C` | `#185FA5` |
| Tools Java et serveur MCP (corail ; MCP en pointillés) | `#FAECE7` | `#993C1D` | `#712B13` | `#993C1D` |
| Hugging Face et modèles (ambre) | `#FAEEDA` | `#854F0B` | `#633806` | `#854F0B` |
| Superviseur (ambre) | `#FAEEDA` | `#854F0B` | `#633806` | `#854F0B` |
| Entrée, contexte, décision du code (gris) | `#F1EFE8` | `#5F5E5A` | `#444441` | `#5F5E5A` |

- Deux types d'agents, visuellement distincts :
  - agents LLM (TalkSearcher, QualityScorer, AgendaPlanner, Superviseur) : bordure renforcée (1.3) et badge arrondi blanc « 🤖 Agent LLM » à la couleur de l'agent, à cheval sur le bord haut ;
  - agents workflow LangChain4j (boucle `loopBuilder`, séquence `sequenceBuilder`) : conteneur en pointillés et badge rectangulaire gris en pointillés « ⚙️ Agent workflow ».
- Les éléments qui ne sont pas des agents (entrée, décision du code, contexte) n'ont pas de badge et gardent une bordure fine.
- Panneaux d'arrière-plan (tours, boucle qualité) : `#FAFAF7`.
- Contexte et état partagé : bandeau gris en pointillés (`stroke-dasharray="4 4"`) en bas du schéma.

## Choix de fond

- La boucle ReAct a trois étapes : Raisonner → Agir → Observer, puis retour à Raisonner.
- Le contexte n'est pas une étape de la boucle : c'est un bandeau partagé, relu par le LLM à chaque tour.
- Rôles explicites : le LLM décide, le code exécute l'outil.
- La sortie de ReAct est décidée par le LLM (« Assez d'infos ? »), avec une limite de tours en garde-fou.
- La boucle qualité BearConf est décidée par le code (score ≥ 0.7 ou 3 tours), pas par le LLM : ne pas la confondre avec la boucle ReAct.
- Pas de détails d'implémentation dans les schémas (noms de clés, etc.).
- Pas de scénario inventé : les sous-titres doivent correspondre à ce que fait réellement la démo.

## Rendu des PNG

Rendu par Chromium headless (Playwright) : chargement du SVG dans une page à fond transparent, suppression du rectangle blanc, capture avec `omit_background=True`. Les emojis s'affichent alors en couleur (Noto Color Emoji), quel que soit le logiciel de présentation.

## Série AI Endpoints et déploiement

- À partir du schéma mémoire : numéro d'étape (pastille foncée) posé sur chaque flèche pour l'ordre d'appel ; pastille claire « 0 » = ingestion faite en amont. Les pastilles sont placées côté départ de la flèche pour ne jamais masquer une pointe. MCP : l'étape « liste des tools » (récupération auprès du serveur MCP) précède la requête au LLM.
- Libellé « Utilisat·rice·eur » (écriture inclusive) partout.
- Flèche pleine = requête, flèche en pointillés = réponse en streaming, double flèche = aller-retour.
- Libellés simplifiés par rapport aux schémas Excalidraw : « T1, T2 … Tn » → « tokens (streaming) », « [x,y,…] » → « vecteur ».
- Générateur Python (`lib.py` + `build.py`) : boîtes, flèches et libellés positionnés sur une grille commune.
