# 🎹 Rewordium AI Keyboard

<div align="center">

![Latest Release](https://img.shields.io/github/v/release/Chethan616/YC_startup?label=🦎%20Latest&color=ff69b4)
![Version](https://img.shields.io/badge/version-1.0.10-blue.svg)
![Platform](https://img.shields.io/badge/platform-Android-green.svg)
![License](https://img.shields.io/badge/license-Proprietary-red.svg)
![Build](https://img.shields.io/badge/build-passing-brightgreen.svg)
![Downloads](https://img.shields.io/github/downloads/Chethan616/YC_startup/total?color=success)

**A powerful, privacy-focused AI keyboard for Android with advanced features and natural typing experience.**

[Features](#-features) • [Installation](#-installation) • [Building](#-building) • [Contributing](#-contributing) • [License](#-license)

</div>

---

## 📖 About

Rewordium is an intelligent Android keyboard that combines the best aspects of modern keyboard design with AI-powered assistance, while maintaining a strong focus on **privacy** and **performance**. Built with Flutter and Kotlin, it delivers a buttery-smooth typing experience with advanced features like:

- **5-tier progressive turbo delete** (Gboard-like acceleration)
- **Premium haptic feedback** tuned for minimal latency
- **AI-powered suggestions** and text transformation
- **Clipboard history management** with favorites
- **Gesture-based navigation** and swipe actions
- **Customizable themes** and layouts
- **Privacy-first design** - your data stays on your device

---

## ✨ Features

### 🎯 Core Features

- **Smooth Typing Experience**
  - Gboard-quality key popups and animations
  - 5-tier turbo delete with natural acceleration (400ms → 35ms)
  - Optimized haptic feedback on every keypress
  - Cached theme panels for instant switching

- **AI Integration**
  - Text paraphrasing and tone adjustment
  - Grammar and spelling correction
  - Smart suggestions powered by AI
  - Translation support

- **Clipboard Management**
  - History panel with instant sync
  - Favorite clipboard items
  - Quick clear and delete actions
  - Persistent storage across restarts

- **Customization**
  - Multiple themes (day/night modes)
  - Adjustable keyboard height
  - Custom key layouts
  - Color and gradient customization

- **Gesture Support**
  - Swipe actions for common operations
  - Gesture-based text selection
  - Quick emoji panel access
  - Toolbar shortcuts

### 🔒 Privacy & Security

- **Local-first processing** - your typing data never leaves your device
- **No keylogging** - we don't track what you type
- **Transparent data usage** - clear about what data is used and why
- **Open contribution model** - review the code yourself

### 🚀 Performance

- **Fast startup** - deferred non-critical initialization
- **Low memory usage** - optimized clipboard and cache management
- **Battery efficient** - minimal background processes
- **Smooth animations** - 60fps+ throughout

---

## 🎬 Latest Release: v1.0.10 "Axolotl"

Current stable version focuses on UI improvements and enhanced turbo delete:

- ✅ Enhanced turbo delete with 5-tier progressive acceleration
- ✅ Experimental feature dialog for transparent communication
- ✅ Fixed dialog overflow issues for all screen sizes
- ✅ Resolved stuck key popup previews
- ✅ Added haptic feedback to backspace

See [AXOLOTL.md](AXOLOTL.md) for full release notes and [playstore_releases/](playstore_releases/) for detailed changelogs.

---

## 📥 Installation

### From GitHub Releases (Recommended)

1. Go to [Releases](https://github.com/Chethan616/YC_startup/releases)
2. Download the latest `v1.0.10-Axolotl.apk`
3. Install on your Android device (enable "Install from Unknown Sources" if needed)
4. Enable Rewordium in **Settings → System → Languages & input → On-screen keyboard**

### From Google Play Store

Coming soon! 🚀

---

## 🏗️ Building from Source

Want to build the app yourself? See our comprehensive [SETUP.md](SETUP.md) guide.

### Quick Start

```bash
# Clone repository
git clone https://github.com/Chethan616/YC_startup.git
cd YC_startup

# Install dependencies
flutter pub get

# Run on connected device
flutter run

# Build release APK
flutter build apk --release
```

**Important:** You'll need to configure:
- `android/key.properties` (signing keys)
- `android/local.properties` (SDK paths, API keys)
- Firebase configuration files
- `.env` file for environment variables

See [SETUP.md](SETUP.md) for detailed instructions.

---

## 🤝 Contributing

We welcome contributions! This project uses a **strict licensing model** that allows contributions while protecting intellectual property.

### How to Contribute

1. **Read the Guidelines**
   - See [CONTRIBUTING.md](CONTRIBUTING.md) for detailed contribution guidelines
   - Review our [Code of Conduct](CODE_OF_CONDUCT.md)

2. **Fork and Clone**
   ```bash
   git fork https://github.com/Chethan616/YC_startup
   git clone https://github.com/YOUR_USERNAME/YC_startup.git
   ```

3. **Create a Feature Branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

4. **Make Your Changes**
   - Follow code style guidelines
   - Add tests for new features
   - Update documentation as needed

5. **Submit a Pull Request**
   - Describe what you changed and why
   - Reference any related issues
   - Ensure all tests pass

### Contribution Areas

We're especially interested in:
- 🐛 Bug fixes and stability improvements
- 🎨 UI/UX enhancements
- 🌍 Localization and translations
- 📝 Documentation improvements
- ⚡ Performance optimizations
- ♿ Accessibility features

---

## 📄 License

**Copyright © 2025 Noxquill Technologies. All Rights Reserved.**

This project uses a **proprietary license with contribution permissions**:

- ✅ **You CAN:** View the source code, contribute improvements, fork for contribution purposes
- ❌ **You CANNOT:** Copy, redistribute, or use commercially without permission
- ⚖️ **Contributors:** By contributing, you agree that your contributions become part of the project under the same license

See [LICENSE](LICENSE) file for full details.

**Why this license?**
- We want transparency and community contributions
- We need to protect our commercial interests
- We aim for a sustainable business model while staying open to collaboration

---

## 🗺️ Roadmap

### Coming Soon

- [ ] iOS version (Flutter compatibility)
- [ ] More AI features (summarization, expansion)
- [ ] Voice input integration
- [ ] Multi-language support (50+ languages)
- [ ] Advanced gesture recognition
- [ ] Plugin system for custom extensions

### Future Plans

- [ ] Cross-platform sync (desktop integration)
- [ ] Advanced theming engine
- [ ] Keyboard sharing marketplace
- [ ] Offline AI models
- [ ] Integration with popular apps

Vote on features and track progress in [Discussions](https://github.com/Chethan616/YC_startup/discussions).

---

## 🙏 Acknowledgments

Rewordium is built with the help of amazing open-source projects:

- **Flutter** - Google's UI framework
- **Kotlin** - Modern Android development
- **Firebase** - Backend services
- **Jetpack Compose** - Modern UI toolkit
- **Lottie** - Animation library
- **Material Design** - Google's design system

Special thanks to our contributors and testers! ❤️

---

## 📞 Contact & Support

- **Website:** https://www.rewordium.tech
- **Support:** [support@rewordium.tech](mailto:support@rewordium.tech)
- **Donations:** https://www.rewordium.tech/donate
- **GitHub Issues:** [Report a bug](https://github.com/Chethan616/YC_startup/issues/new)
- **Discussions:** [Ask questions](https://github.com/Chethan616/YC_startup/discussions)

---

## 📊 Project Stats

- **Current Version:** 1.0.10 "Axolotl"
- **Release Date:** November 7, 2025
- **Target Android:** 7.0+ (API 24+)
- **Language:** Dart, Kotlin, Java
- **Build System:** Flutter 3.35.4, Gradle 8.7.0

---

## 🌟 Star History

If you like this project, please give it a ⭐ on GitHub!

---

<div align="center">

**Made with ❤️ by Noxquill Technologies**

[⬆ Back to Top](#-rewordium-ai-keyboard)

</div>
