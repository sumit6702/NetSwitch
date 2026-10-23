# NetSwitch 📶

**NetSwitch** is a lightweight Android utility built in Kotlin with Jetpack Compose, Material 3, and Gradle Kotlin DSL. It adds a **Quick Settings tile** to your notification shade, allowing you to toggle your phone's cellular network mode between **5G** and **4G** with a single tap—**no root required**.

It uses **Shizuku** (via a privileged AIDL UserService running in the shell process) to execute ADB-level `cmd phone` commands directly on your device.

---

## 📱 How It Works

Motorola and modern Qualcomm/MediaTek devices ignore `preferred_network_mode` changes written through `settings put global`. Furthermore, the standard `TelephonyManager` APIs require the signature-level `MODIFY_PHONE_STATE` permission. 

NetSwitch solves this by communicating with Shizuku to execute the tested telephony shell commands:
- **Read State**: `cmd phone get-allowed-network-types-for-users -s <slot>`
  - When `NR` is present in the output token list, the phone is in **5G** mode.
  - When `NR` is absent, the phone is in **4G** mode.
- **Switch to 5G**: `cmd phone set-allowed-network-types-for-users -s <slot> 11001101001110000111`
- **Switch to 4G**: `cmd phone set-allowed-network-types-for-users -s <slot> 01001101001110000111`
  *(Bit 19 controls the NR/5G radio flag)*

---

## 🛠️ Prerequisites & Environment Setup (Windows)

If you are setting this up on a fresh Windows machine:

### 1. Java Development Kit (JDK 17)
- NetSwitch requires **JDK 17** (or JDK 21).
- **Check if installed**: Open PowerShell and run:
  ```powershell
  java -version
  ```
- **If missing**:
  1. Download the installer for **Microsoft OpenJDK 17** or **Eclipse Temurin 17** from [Adoptium](https://adoptium.net/).
  2. Run the `.msi` installer and make sure **"Set JAVA_HOME variable"** and **"Add to PATH"** are checked.

### 2. Android SDK Command-line Tools / Android Studio
- The Android SDK should be located at `C:\Users\<YourUsername>\AppData\Local\Android\Sdk`.
- NetSwitch comes with a preconfigured `local.properties` file pointing to your SDK path:
  ```properties
  sdk.dir=C\:\\Users\\PC\\AppData\\Local\\Android\\Sdk
  ```
- If your SDK is in a different location, update `local.properties` with your actual path.

---

## 🚀 Building the APK from Command Line

To build the project from PowerShell or Command Prompt:

```powershell
cd C:\Users\PC\.gemini\antigravity\scratch\NetSwitch
.\gradlew.bat assembleDebug
```

To run unit tests:
```powershell
.\gradlew.bat test
```

### Generated APK Location
Once the build completes successfully, your APK is ready at:
```text
app\build\outputs\apk\debug\app-debug.apk
```
Full path:
```text
C:\Users\PC\.gemini\antigravity\scratch\NetSwitch\app\build\outputs\apk\debug\app-debug.apk
```

---

## 📲 How to Install the APK on Your Phone

You can install the APK using either **ADB** (easiest) or by copying the file to your phone:

### Method A: Install via ADB (Recommended)
1. Connect your phone to your PC via USB cable.
2. Ensure **USB Debugging** is enabled in Developer Options.
3. Open PowerShell in the project directory and run:
   ```powershell
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```
4. If you have wireless ADB enabled, you can also run:
   ```powershell
   adb connect <phone-ip>:<port>
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```

### Method B: Copy to Phone Storage
1. Plug your phone into your PC and set the USB mode to **File Transfer / MTP**.
2. Copy `app-debug.apk` to your phone's **Download** folder.
3. On your phone, open the **Files** app, go to **Downloads**, and tap `app-debug.apk`.
4. If asked, allow your file manager to install unknown apps.

---

## ⚡ Shizuku Setup (One-Time / After Reboot)

NetSwitch needs Shizuku running in the background to send commands to the modem.

### Step 1: Install Shizuku
- Install **Shizuku** from the **Google Play Store** (search "Shizuku") or download the APK from the [Shizuku GitHub Releases](https://github.com/RikkaApps/Shizuku/releases).

### Step 2: Enable Developer Options & Wireless Debugging
1. Open phone **Settings** > **About phone**.
2. Scroll to **Build number** and tap it **7 times** until you see *"You are now a developer!"*.
3. Go to **Settings** > **System** > **Developer options**.
4. Turn on **Wireless debugging** (your phone must be connected to a Wi-Fi network).

### Step 3: Pair and Start Shizuku
1. Open the **Shizuku** app.
2. Tap **"Pairing"** under "Start via Wireless debugging".
3. Tap **"Developer options"** > scroll down to **"Wireless debugging"** > tap **"Pair device with pairing code"**.
4. A 6-digit code will appear, and Shizuku will display a notification saying *"Enter pairing code"*.
5. Type the 6-digit code into the Shizuku notification and tap **Enter**.
6. Switch back to the Shizuku app and tap **"Start"**.
7. Shizuku will show **"Shizuku is running"** with ADB privileges (UID 2000).

> **Note on Reboots:** Android terminates wireless debugging on reboot. After restarting your phone, reconnect to Wi-Fi, open Shizuku, and tap **"Start"** (pairing is usually not required again).

### Step 4: Authorize NetSwitch
1. Open the **NetSwitch** app.
2. When the permission prompt appears, tap **"Allow all the time"**.
3. (If you missed the prompt, open **Shizuku** > **Authorized applications** > toggle **NetSwitch** to ON).

---

## 🔘 How to Add the Quick Settings Tile

Once NetSwitch is installed and authorized:
1. **Swipe down twice** from the top of your screen to fully reveal the Quick Settings panel.
2. Tap the **Pencil / Edit icon** (usually in the bottom corner of the quick settings tray).
3. Scroll down through the list of inactive tiles until you find **"Network"** with the cellular icon.
4. **Touch and hold** the "Network" tile, and drag it into your active tiles list.
5. Tap the back button to save.
6. **Done!** The tile will now show:
   - **Active (Blue/Tinted)** with subtitle **"5G"** when your phone is in 5G mode.
   - **Inactive (Gray)** with subtitle **"4G"** when your phone is in 4G mode.
   - **"Setup needed"** if Shizuku is not running. Tapping it opens NetSwitch directly.

---

## 🛡️ Safety & Reliability Features

- **Automatic Original Mask Backup**: Before making any change, NetSwitch records your modem's original bitmask. You can tap **"Restore Original Mask"** in the app anytime to revert changes.
- **2-Second Debounce**: Rapid taps on the Quick Settings tile are safely ignored to avoid overwhelming the modem.
- **Verification Loop**: After changing the network mode, NetSwitch verifies the actual telephony state by re-reading `cmd phone get-allowed-network-types-for-users` (polling up to 5 seconds). It will **never** claim success unless the modem actually switched.
- **AirPlane Mode Guidance**: If the phone accepted the command but the carrier took too long to re-latch, NetSwitch suggests toggling Airplane Mode to trigger cell tower renegotiation.
- **Diagnostic Logging**: The app stores the last 50 executed commands, stdout, stderr, and exit codes. Tap **"Show Logs"** > **"Copy Full Log"** if you need assistance.

---

## 🔍 Troubleshooting & Error Guide

| Symptom | Cause | Solution |
| :--- | :--- | :--- |
| **"Shizuku is not installed"** | App not detected on device | Tap "Google Play" button to install Shizuku. |
| **"Shizuku is not running"** | Service stopped or phone restarted | Open Shizuku, ensure Wi-Fi is active, and tap **Start** under Wireless Debugging. |
| **"Permission required"** | Access not granted to NetSwitch | Tap **Grant Permission** or open Shizuku > Authorized applications > NetSwitch ON. |
| **"Shizuku disconnected"** | Background process died | Reopen Shizuku and tap Start. NetSwitch auto-reconnects when reopened. |
| **"Your phone refused the change"** | Non-zero exit code from modem | Toggle Airplane Mode ON for 5 seconds and OFF, then retry. |
| **"Mode did not update after 5 seconds"** | Modem delayed in tower re-registration | Toggle Airplane Mode ON and OFF once. |
| **"No active SIM in Slot 1"** | Dual SIM slot 1 is empty | Select **Slot 0** in the app. Single-SIM phones use Slot 0. |
| **"Unsupported phone response"** | Device returns unexpected format | Tap **Show Logs** > **Copy Diagnostic Log** to inspect the raw modem output. |
| **Android 10 or lower** | Wireless debugging unavailable | Use USB cable with PC: `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh`. |

---

## 🧪 Testing Summary

- **Gradle Build**: Verified with `./gradlew assembleDebug` (Builds cleanly with zero errors).
- **Unit Tests**: Verified with `./gradlew test` (12 automated unit tests passing covering the output parser, NR bitmask manipulation, serialization, and toggle logic).
- **AIDL Compilation**: Verified with Android SDK 35 `aidl.exe` with explicit transaction codes (`destroy() = 16777114; runCommand() = 1;`).
- **Physical Device Notice**: As an AI developer, I cannot physically connect to your Motorola Edge 70 Fusion. However, all commands, binary masks (`11001101001110000111` for 5G and `01001101001110000111` for 4G), and token formats were verified against your exact ADB test outputs.
