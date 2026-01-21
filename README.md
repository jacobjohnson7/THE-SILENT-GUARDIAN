

# 🛡️ The Silent Guardian: Real-Time Sound Detection App

![Status](https://img.shields.io/badge/Status-Development-yellow)
![Platform](https://img.shields.io/badge/Platform-Android-green)
![AI](https://img.shields.io/badge/AI-TensorFlow%20Lite-blue)

**The Silent Guardian** is an Android-based assistive application designed to improve the safety and situational awareness of individuals with hearing impairments. It detects critical environmental sounds in real time and alerts users through vibrations and visual notifications — all without requiring internet connectivity.

## 🚀 Key Features

* **Real-Time Detection:** Identifies sounds like fire alarms, sirens, door knocks, and distress calls.
* **On-Device AI:** Uses TensorFlow Lite for fast, private, and offline sound classification.
* **Low Latency:** Processes short audio frames locally for immediate response.
* **Privacy First:** No cloud processing or audio storage.
* **Assistive Alerts:** Strong vibration patterns and high-contrast visual warnings.

## 🛠️ Tech Stack

* **Language:** Java
* **IDE:** Android Studio
* **Machine Learning:** TensorFlow Lite
* **Audio Processing:** Android AudioRecord API
* **Architecture:** Single-Module Intelligent Processing

## 📱 How It Works

1. **Audio Capture:** The app continuously listens using the device microphone.
2. **Preprocessing:** Audio is converted into fixed-size frames.
3. **Inference:** The ML model classifies sounds on-device.
4. **Decision Logic:** High-priority events are identified.
5. **Alerting:** Immediate vibration and visual alerts are triggered.

## 📦 Setup & Installation

1. Clone this repository.
2. Open in **Android Studio**.
3. Connect an Android device (USB Debugging enabled).
4. Build & Run the application.
5. Grant microphone and background execution permissions.

## 📄 License

This project is licensed under the MIT License – see the [LICENSE](LICENSE) file for details.

---

*Final Year MCA Project | 2025*


