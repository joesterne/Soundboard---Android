# Soundboard

A highly customizable and intelligent soundboard application built with Android, Kotlin, and Jetpack Compose. 

## Features

### 🎛️ Dynamic Audio Grid
*   **Customizable Layout:** Adjust the grid dimensions (rows and columns) to fit as many or as few sounds as you need.
*   **Audio Import & Recording:** Import existing audio files directly or record new sounds on the fly using your device's microphone.
*   **Per-Tile Customization:** Assign custom colors, names, and relative volumes to each individual sound tile.
*   **Advanced Playback Controls:** Trim your audio clips directly in the app and set sounds to loop continuously.

### 🌟 Favorites System
*   **Global Pinning:** Favorite your most-used sounds to save them into an independent local database.
*   **Cross-Preset Access:** Access your pinned favorites from a dedicated navigation drawer, instantly playable regardless of which preset board is currently loaded.

### 🔊 Master Volume Control
*   **Relative Scaling:** A global master volume slider seamlessly scales the output of all individual sound tiles while perfectly preserving their unique per-tile relative volume balances.

### 🧠 Intelligent Auto-Arrange
*   **Usage Tracking:** The app automatically tracks the playback frequency (`playCount`) of your sounds.
*   **Smart Sorting:** With the tap of a button in the top app bar, organize your soundboard so your most frequently used sounds migrate to the top-left, followed by alphabetical sorting, pushing empty tiles to the back.

### 💾 Presets & Sharing
*   **Preset Packs:** Save different board configurations as presets for different occasions.
*   **Export/Import:** Share your soundboards by exporting them as ZIP files, complete with audio assets and layouts, and import them seamlessly.

### 🎨 Visual Identity
*   **Custom Backgrounds:** Personalize the app by selecting custom background images and global color themes.

## Tech Stack
*   **Language:** Kotlin
*   **UI Framework:** Jetpack Compose (Material Design 3)
*   **Architecture:** MVVM (Model-View-ViewModel)
*   **Local Persistence:** Room Database for saving tiles, preferences, favorites, and usage telemetry. 
*   **Audio Pipeline:** Native `SoundPool` for low-latency playback, coupled with `MediaPlayer` for advanced playback controls (looping, trimming, and precise volume adjustment).

## Usage
*   **Long-Press** a tile to open its edit menu (assign audio, trim, color, loop, favorite).
*   **Tap** the sort icon in the top right to Auto-Arrange.
*   **Swipe** to open the side drawer to access Favorites, Settings, and Presets.
