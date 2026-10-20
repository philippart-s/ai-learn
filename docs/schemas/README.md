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
| `bearconf-supervisor` | Mode superviseur (`_09_SupervisorChatbot.java`) : un agent LLM superviseur choisit quel agent appeler |

Chaque schéma existe en SVG (source à modifier) et en PNG (à insérer dans les slides).

## Format

- 16:10, `viewBox="0 0 680 425"`, exporté en 1600×1000.
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
