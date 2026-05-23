# ✨ Rewordium — AI Writing Assistant & Keyboard

<p align="center">
  <img alt="Rewordium Banner" src="https://img.shields.io/badge/Rewordium-AI_Writing_Suite-brightgreen?style=for-the-badge&labelColor=111&color=32CD32" />
</p>

<div align="center">

<a href="https://github.com/Chethan616/Rewordium/releases/latest">
  <img alt="Latest Release" src="https://img.shields.io/github/v/release/Chethan616/Rewordium?label=%F0%9F%A6%8E%20Latest&color=32CD32">
</a>
<img alt="Version" src="https://img.shields.io/badge/version-1.0.10%2B10-white.svg">
<img alt="Platform" src="https://img.shields.io/badge/platform-Android-brightgreen.svg">
<img alt="License" src="https://img.shields.io/badge/license-Proprietary-black.svg">
<img alt="Build" src="https://img.shields.io/badge/build-passing-brightgreen.svg">
<a href="https://github.com/Chethan616/Rewordium/releases">
  <img alt="Downloads" src="https://img.shields.io/github/downloads/Chethan616/Rewordium/total?color=32CD32">
</a>

---

**Rewordium** is a next-generation **AI writing assistant & keyboard** for Android.  
Rewrite, summarize, refine, and write anywhere — *privately, beautifully, intelligently.*

[🌊 Features](#-features) • [📥 Installation](#-installation) • [🧠 AI Tools](#-ai-tools) • [🏗️ Building](#-building-from-source) • [🎨 Visuals](#-visual-experience) • [🛠️ Tech Stack](#-tech-stack) • [🤝 Contributing](#-contributing)

</div>

---

## About

Rewordium combines the power of **AI writing tools**, a **smart keyboard**, and a **floating rewrite assistant** — all built with **privacy at its core**.  
It’s designed for creators, professionals, and anyone who values effortless, intelligent writing.

> “Like the Axolotl, Rewordium regenerates — your creativity, your words, your ideas.”

---

## ✨ Features

### 🧠 AI Writing Tools
- ✍️ Rewrite, summarize, and refine text instantly  
- 🎭 Adjust tone — formal, creative, or concise  
- 💬 Works across any app via the floating AI bubble  
- ⚡ Lightning-fast response with privacy-first AI  

### 🎹 Intelligent Keyboard
- 🚀 Five-tier **Turbo Delete** acceleration  
- 🎧 Premium haptics and smooth key transitions  
- 💡 Smart clipboard integration  
- 🌗 Adaptive theme and animation transitions  

### 🔒 Privacy & Security
- No tracking, no ads, no data collection  
- Local-first text processing  
- Transparent permission and privacy controls  

### 🎨 Design & Customization
- Light/dark green gradient themes  
- Adjustable keyboard height and spacing  
- Fluid, minimal interface with responsive design  

---

## 🦎 Latest Release — *v1.0.10 “Axolotl”*

> *Regeneration through refinement.*

- ✅ Turbo Delete 2.0 — smoother acceleration  
- ✅ Fixed overflow in experimental dialog  
- ✅ Refined popup animations  
- ✅ Improved haptics and transitions  

📄 [Full Changelog →](playstore_releases/RELEASE_NOTES_AXOLOTL.md)

---

## 📥 Installation

### From GitHub
1. Visit [**Releases**](https://github.com/Chethan616/Rewordium/releases)  
2. Download the latest build (for example, `v1.0.10-Axolotl.apk`)  
3. Transfer it to your Android device or download directly  
4. Install it (you may need to allow *Install from Unknown Sources*)  
5. Enable Rewordium in  
   `Settings → System → Languages & input → On-screen keyboard`  
6. Open the app to customize theme, keyboard height, haptics, and preferences  

---

### From Google Play
<a href="https://play.google.com/store/apps/details?id=com.noxquill.rewordium" style="text-decoration:none">
  <img alt="Play Store" src="https://img.shields.io/badge/📱%20Get%20it%20on-Google%20Play-brightgreen?style=for-the-badge&logo=google-play&logoColor=white" />
</a>  

**Package name:** `com.noxquill.rewordium`

---

## 🧠 AI Tools

| Tool | Description |
|------|-------------|
| ✍️ **Rewrite** | Reword sentences naturally with AI |
| 🧾 **Summarize** | Condense long text into concise ideas |
| 🎭 **Tone Shift** | Instantly switch tone between creative, formal, or casual |
| 🪶 **Grammar Fix** | Correct grammar, punctuation, and flow |
| 💬 **Ask AI** | Get rewriting help anywhere, inside any app |

---

## 🧩 AI Engine Flow

The following diagram reflects the **exact production architecture** as implemented in the codebase.

```mermaid
flowchart TD
    U([👤 User]) --> INPUT

    subgraph INPUT["📥 Text Input Layer"]
        direction TB
        EDITOR["✍️ FocusedEditor\n(flutter_quill)"]
        IMPORT["📂 DocumentService\nPDF · DOCX · TXT · MD · URL"]
        SCAN["📷 CunningDocumentScanner\n+ MLKit TextRecognizer"]
        KB["⌨️ Reboard Keyboard\nIME (AOSP/FlorisBoard fork)"]
    end

    INPUT --> ENGINE

    subgraph ENGINE["🔀 UnifiedAIService · Provider Router"]
        direction LR
        GATE{"🔐 Firebase Auth\nGate"}
        GATE -->|logged in| CRED{"💳 Credit Check\nFirebaseService"}
        CRED -->|sufficient| PROVIDER

        subgraph PROVIDER["LLM Provider"]
            GROQ["🟢 Groq\nqwen/qwen3-32b\n(default)"]
            GEMINI["🔵 Gemini\ngenerativelanguage API"]
            OAI["⚫ OpenAI\nGPT-4o / GPT-4"]
            CLAUDE["🟠 Anthropic\nClaude 3.x"]
            CUSTOM["⚙️ Custom\nOpenAI-compat endpoint"]
        end

        PROVIDER --> SANITIZE["🧹 _sanitizeUserText\n_stripThinkTags\n_extractJson"]
    end

    subgraph TOOLS["🛠 AI Writing Tools"]
        direction TB
        PARA["✍️ Paraphraser\nTone modes + Custom prompt"]
        GRAM["🪶 Grammar Check\nCorrections + Error list"]
        SUMM["🧾 Summarizer\nSummary + Key points"]
        TONE["🎭 Tone Editor\nChange tracking"]
        TRANS["🌐 Translator\n100+ languages + Notes"]
        DETECT["🛡️ AI Detector\nPer-sentence scores"]
        JADE["💬 Jade AI Chat\nPersona-aware chatbot"]
        KB_AI["⚡ Keyboard Quick Rewrite\nFloating overlay"]
    end

    ENGINE --> TOOLS
    TOOLS --> OUT["📤 Result Output\n(same FocusedEditor view)"]
    OUT --> U

    subgraph CHUNKING["📄 DocumentChunkingService"]
        CHK["Splits text > limit\ninto parallel chunks\nthen merges results"]
    end

    IMPORT --> CHUNKING
    CHUNKING --> ENGINE

    subgraph ANALYTICS["📊 UsageAnalyticsService"]
        ANA["Records provider · feature\ncredits used · errors\nFirestore"]
    end

    ENGINE --> ANALYTICS

    style ENGINE fill:#1a1a2e,stroke:#6C63FF,color:#fff
    style PROVIDER fill:#16213e,stroke:#4CAF50,color:#fff
    style GROQ fill:#00875A,color:#fff
    style GEMINI fill:#1A73E8,color:#fff
    style OAI fill:#333,color:#fff
    style CLAUDE fill:#D97706,color:#fff
    style CUSTOM fill:#555,color:#fff
    style TOOLS fill:#0f3460,stroke:#e94560,color:#fff
    style INPUT fill:#1a1a2e,stroke:#32CD32,color:#fff
    style CHUNKING fill:#1e293b,stroke:#64748b,color:#fff
    style ANALYTICS fill:#1e293b,stroke:#64748b,color:#fff
```

### Key Architecture Notes

| Component | Implementation |
|-----------|---------------|
| **Default LLM** | `qwen/qwen3-32b` via Groq Cloud (`UnifiedAIService._makeGroqRequest`) |
| **Think-tag stripping** | `_stripThinkTags()` removes `<think>…</think>` from all Qwen3 output |
| **JSON extraction** | `_extractJson()` tolerates markdown fences + stray prose from all models |
| **Credit system** | 1 credit deducted per successful default-provider call via `FirebaseService.consumeCredit` |
| **Large docs** | `DocumentChunkingService` splits text, runs parallel AI calls, merges results |
| **Document import** | `DocumentService` handles PDF (Syncfusion), DOCX (archive+xml), TXT/MD (dart:io), URL (HTTP + BeautifulSoup) |
| **OCR scanning** | `CunningDocumentScanner` → `MLKit TextRecognizer(script: TextRecognitionScript.latin)` |
| **Keyboard AI** | Reboard IME sends text to `GroqService` directly via `RewordiumKeyboardService` |
| **Custom providers** | Gemini, OpenAI, Anthropic, and any OpenAI-compat endpoint — user-configured in Advanced AI Settings |


---
## 🏗️ Building From Source

You can easily build Rewordium from source using Flutter:

```bash
git clone https://github.com/Chethan616/Rewordium.git
cd Rewordium
flutter pub get
flutter run
flutter build apk --release
```

---

## Attribution

Rewordium includes modified keyboard components based on FlorisBoard.

- FlorisBoard: https://github.com/florisboard/florisboard
- License: Apache License 2.0
- Copyright: Patrick Goldinger and contributors
- Notices: See NOTICE and in-app Credits & Licenses