# Soundboard

A highly customizable and intelligent soundboard application built with Android, Kotlin, and Jetpack Compose. 

## Features

### 🎛️ Dynamic Audio Grid
*   **Customizable Layout:** Adjust the grid dimensions (rows and columns) to fit as many or as few sounds as you need.
*   **Audio Import & Recording:** Import existing audio files directly or record new sounds on the fly using your device's microphone.
*   **Batch Import:** Select multiple audio files at once from device storage with automatic board capacity expansion to populate empty grid tiles simultaneously.
*   **Per-Tile Customization:** Assign custom colors, names, and relative volumes to each individual sound tile.
*   **Tempo & Playback Speed:** Fine-tune audio playback speed per tile from 0.5x up to 2.0x with live preview and badging for custom tempos.
*   **Advanced Playback Controls:** Trim your audio clips directly in the app and set sounds to loop continuously.

### 🎵 AI Prompt-Based Sound & Music Generation
*   **Prompt-to-Audio:** Generate brand-new sound effects, loops, beats, and musical compositions simply by typing a natural language prompt (e.g. "80s synthwave beat with heavy punchy bass" or "dramatic cinematic drum swell").
*   **Google DeepMind Lyria Models:**
    *   `lyria-3-clip-preview`: For rapid generation of sound effects, punches, beats, and short clips up to 30 seconds.
    *   `lyria-3-pro-preview`: For longer, rich full-length musical compositions and tracks.
*   **Live Preview & Seamless Assignment:** Listen to generated tracks inside the dialog before applying them directly to individual tiles, or let the app automatically find and expand empty tiles to host them.

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
*   **Tap** any configured tile to play its audio with haptic feedback.
*   **Double-Tap** any two tiles in succession to instantly swap their positions.
*   **Long-Press** a tile to open its edit dialog (sound assignment, AI generation, playback speed slider, volume, trim range, color, looping, and favoriting).
*   **Generate Sound with AI**: 
    *   Tap the **AutoAwesome (`✨`)** icon in the top app bar or drawer menu to open the generation dialog, type your description (or pick an inspiration chip), select *Short Clip* (`lyria-3-clip-preview`) or *Full Track* (`lyria-3-pro-preview`), preview the audio, and apply it.
    *   Alternatively, long-press any tile and tap **AI Sound** to generate and assign audio directly to that specific tile.
*   **Batch Import**: Tap the `LibraryAdd` icon in the top app bar or open the drawer menu to bulk import multiple audio files at once.
*   **Auto-Arrange**: Tap the sort icon in the top right to organize tiles by usage frequency and alphabetical name.
*   **Side Drawer**: Swipe from the left or tap the menu icon to access AI Sound Generation, Favorites, Presets, Web Sound Search, and Board Settings.

## Configuration & API Keys
*   **AI Sound Generation:** Powered by Gemini / DeepMind Lyria models. Configure your `GEMINI_API_KEY` in the **Secrets panel** in AI Studio (or `.env` file). The app automatically accesses this via `BuildConfig.GEMINI_API_KEY`.
