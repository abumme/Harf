# Harf Game

### Android
To run the application on android device/emulator:  
 - open project in Android Studio and run imported android run configuration  

To build the application bundle:  
 - run `./gradlew :androidApp:assembleDebug`  
 - find `.apk` file in `androidApp/build/outputs/apk/debug/androidApp-debug.apk`  

### Desktop
Run the desktop application: `./gradlew :desktopApp:run`  
Run the desktop **hot reload** application: `./gradlew :desktopApp:hotRun --auto`  

### iOS
To run the application on iPhone device/simulator:  
 - Open `iosApp/iosApp.xcproject` in Xcode and run standard configuration  
 - Or use [Kotlin Multiplatform Mobile plugin](https://plugins.jetbrains.com/plugin/14936-kotlin-multiplatform-mobile) for Android Studio  

### Web Distribution
Build web distribution: `./gradlew :webApp:composeCompatibilityBrowserDistribution`  
Deploy a dir `webApp/build/dist/composeWebCompatibility/productionExecutable` to a web server  

### JS Browser
Run the browser application: `./gradlew :webApp:jsBrowserDevelopmentRun`  

### Wasm Browser
Run the browser application: `./gradlew :webApp:wasmJsBrowserDevelopmentRun`  

### Backend & Local Development Setup

1. **Start the database:**
   Start the PostgreSQL container via Docker Compose:
   ```bash
   docker compose up -d db
   ```

2. **Run the backend server:**
   Start the Ktor backend application (runs on `http://0.0.0.0:8080` by default):
   ```bash
   ./gradlew :backend:run
   ```

3. **Run backend tests:**
   ```bash
   ./gradlew :backend:test
   ```

4. **Connecting `:sharedUI` to the backend:**
   The client defaults to `http://localhost:8080` for Desktop. For Android emulators or physical test devices, configure the server host/IP (e.g. `http://10.0.2.2:8080` for Android emulator).

