# 🔥 FireWeb TV: Complete Fire TV Installation & Setup Guide

This guide walks you through setting up and running any website as a clean, ad-free app on your Amazon Fire TV (Fire TV Stick 4K / Max / Lite / Cube / Omni TV).

---

## Step 1: Prepare Your Fire TV (Enable Sideloading)

Fire TV devices allow installing any Android APK natively. You just need to enable "Developer Options" once:

1. On your Fire TV, go to **Settings (gear icon)** > **My Fire TV** (or *Device & Software*).
2. Click **About**.
3. Highlight your device name (e.g., *Fire TV Stick 4K*) and press the **Center Select button 7 times rapidly**.
   - You will see an on-screen toast message: *"No need, you are already a developer."*
4. Press **Back** once. You will now see a new menu: **Developer Options**.
5. Click **Developer Options**:
   - Turn **ADB Debugging** to **ON**.
   - Click **Install unknown apps** (or *Apps from Unknown Sources*) and ensure your installer (e.g. **Downloader**) is set to **ON**.

---

## Step 2: Install FireWeb TV onto Your Fire TV

Choose whichever method is easiest for you:

### Method A: Direct Install via the "Downloader" App (No PC Required!)

1. On your Fire TV home screen, go to **Find / Search** and type **Downloader**.
2. Install the orange **Downloader** app by AFTVnews (it's free on the Amazon Appstore).
3. Open **Downloader**, grant storage permissions when prompted.
4. You can host the compiled `fireweb-tv.apk` on a local link or cloud drive (e.g., Google Drive, Dropbox, GitHub Releases, or a simple local Python server `python3 -m http.server 8000` on your Mac).
5. In Downloader's URL box, enter your download link (e.g. `http://192.168.1.50:8000/fireweb.apk`).
6. Click **Go** -> Downloader will download the file and immediately prompt you to click **Install**.
7. Click **Done** or **Open**!

---

### Method B: Wireless ADB from Mac/PC (Fastest for Developers)

If your Mac/PC and Fire TV are connected to the same Wi-Fi network:

1. Find your Fire TV's IP address:
   - On Fire TV: **Settings** > **My Fire TV** > **About** > **Network** (e.g. `192.168.1.120`).
2. On your Mac terminal:
   ```bash
   # Connect wirelessly to your Fire TV
   adb connect 192.168.1.120:5555
   
   # Install FireWeb TV
   adb install -r fireweb-tv.apk
   ```
3. A prompt will appear on your TV screen: *"Allow USB/ADB debugging from this computer?"* Check **Always allow** and select **OK**.
4. Installation succeeds in seconds, and FireWeb TV will appear in your **Apps & Channels** list!

---

## Step 3: Using FireWeb TV on Your Television

### 🎮 Fire TV Remote Shortcuts

| Remote Button | Action |
|---|---|
| **D-Pad (Up/Down/Left/Right)** | Move focus on Dashboard • Scroll web page • Move Virtual Mouse Cursor |
| **Center (Select)** | Launch app • Click links/buttons under Virtual Cursor |
| **Menu Button (☰ - 3 lines)** | **Toggle Virtual Mouse Cursor Mode ON / OFF** |
| **Play / Pause** | Play/pause active HTML5 video stream on web pages |
| **Fast Forward (>>)** | Seek forward +10 seconds in video |
| **Rewind (<<)** | Seek backward -10 seconds in video |
| **Back Button (↩)** | Go back one web page • Exit fullscreen video • Return to Hub |

---

## Step 4: The Phone / Laptop Companion Portal (No Typing on TV!)

Typing long website URLs with a TV remote is tedious. FireWeb TV includes a built-in local web portal:

1. Open FireWeb TV on your television.
2. Look at the top status bar: you will see your local connection link:
   ```
   Phone / Laptop URL: http://192.168.1.XXX:8080
   ```
3. On your smartphone or computer connected to the same home Wi-Fi, open your browser and type that address (e.g., `http://192.168.1.120:8080`).
4. You will see the **FireWeb TV Companion Portal**:
   - **⚡ Open Instant URL on TV**: Paste any movie, anime, sports, or website link and click **"Open on Fire TV"** — the TV opens it instantly with ad-blocking!
   - **➕ Save New Web App**: Add custom websites with your preferred name, category, ad-block toggle, and mouse settings.
   - **Manage Saved Apps**: Remove or reorder apps from the comfort of your phone.

---

## Step 5: Generating Dedicated Standalone Apps

If you want a specific website to have its own dedicated icon on the Fire TV home screen (rather than launching via the Hub):

Run the included generator tool on your computer:
```bash
cd fireweb-tv/cli
node generator.js --name "HiAnime TV" --url "https://hianime.to" --adblock true
```
This generates a standalone Android TV project configured specifically for that website with its own app ID, ready to build and install.
