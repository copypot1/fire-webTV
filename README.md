# 🔥 FireWeb TV: Run Any Website as an Ad-Free App on Fire TV

**FireWeb TV** transforms any website into a clean, 10-foot television application designed specifically for **Amazon Fire TV** (Fire OS / Android TV). It eliminates intrusive banner ads, video popups, and click traps, while providing an intuitive virtual mouse cursor controlled directly by your Fire TV remote.

---

## ✨ Features

- 🛡️ **Built-in Ad & Tracker Blocker**:
  - Intercepts requests to over 100+ major ad networks, video preroll providers, and telemetry trackers.
  - Injects universal cosmetic CSS to automatically remove banner ad frames, cookie prompts, and clutter.
  - Blocks unwanted pop-up tabs, redirects, and clickjack traps common on free media sites.

- 🎯 **TV Remote Virtual Mouse Cursor**:
  - Full D-pad directional control with smooth movement acceleration.
  - Press the remote's **Select** button to click links, scrub video timelines, and open complex dropdowns.
  - One-click toggle using the Fire TV remote's **Menu (☰)** button to switch between scroll mode and virtual mouse mode.

- 📺 **Fullscreen HTML5 Video Engine**:
  - Automatic fullscreen expansion for HTML5 web videos.
  - Fire TV remote media buttons (Play, Pause, Fast Forward, Rewind) mapped directly to online video players.

- 📱 **Phone & Laptop Companion Web Portal**:
  - Serves an embedded mobile-friendly dashboard over local Wi-Fi at `http://<firetv-ip>:8080`.
  - Push URLs from your phone or laptop directly to your TV screen in real time.
  - Add, edit, and organize web apps without typing with the TV remote.

- 📦 **Dual Operating Modes**:
  1. **FireWeb Hub**: An all-in-one TV app with customizable bookmarks and preconfigured services.
  2. **Standalone App Generator CLI**: Generate dedicated standalone `.apk` projects for individual favorite websites.

---

## 📂 Project Structure

```
fireweb-tv/
├── android/                        # Android TV / Fire OS project
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── AndroidManifest.xml # Leanback TV configuration & permissions
│   │   │   ├── assets/
│   │   │   │   ├── adblock_domains.txt    # Network request ad domain blocklist
│   │   │   │   ├── adblock_cosmetic.css   # Element hiding & cosmetic rules
│   │   │   │   └── default_apps.json      # Pre-configured TV web apps
│   │   │   ├── java/com/fireweb/tv/
│   │   │   │   ├── MainActivity.java      # 10-foot TV dashboard
│   │   │   │   ├── WebActivity.java       # Fullscreen TV web runner & video engine
│   │   │   │   ├── AppManager.java        # App persistence & state management
│   │   │   │   ├── adblock/
│   │   │   │   │   └── AdBlockEngine.java # Fast domain matching & cosmetic injector
│   │   │   │   ├── ui/
│   │   │   │   │   └── VirtualCursorOverlay.java # D-pad mouse cursor & touch synthesis
│   │   │   │   ├── server/
│   │   │   │   │   └── CompanionServer.java # Local Wi-Fi phone companion portal (8080)
│   │   │   │   └── model/
│   │   │   │       └── WebApp.java        # Data model
│   │   │   └── res/                       # TV layouts, drawables, selectors, and themes
│   │   └── build.gradle
│   └── build.gradle
├── cli/                            # Standalone App Generator & Test Suite
│   ├── generator.js               # CLI generator script
│   ├── test-adblock.js            # Automated verification test suite
│   └── package.json
└── docs/
    └── INSTALLATION_GUIDE.md      # Step-by-step sideloading & remote control guide
```

---

## 🚀 Getting Started

### 1. Run Automated Verification Tests
```bash
cd fireweb-tv/cli
node test-adblock.js
```

### 2. Generate a Standalone App for Any Website
```bash
node fireweb-tv/cli/generator.js --name "My Anime App" --url "https://hianime.to" --adblock true
```

### 3. Build & Install on Fire TV
See the complete step-by-step instructions in [INSTALLATION_GUIDE.md](docs/INSTALLATION_GUIDE.md).
