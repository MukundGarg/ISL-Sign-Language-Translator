# ISL Sign Language Translator (Offline, On-Device AI)

An Android app that translates Indian Sign Language (ISL) hand gestures into text in real-time **completely offline**, using on-device AI for privacy, low latency, and zero internet dependency.

## Features

- **Offline Gesture Recognition**: Fully functional without internet.
- **Real-Time Translation**: Converts ISL hand signs to letters and words instantly.
- **Full A–Z Recognition**: Detects all alphabet letters.
- **Fixed Word Gestures**: Recognizes HELLO, THANK_YOU, YES, NO, PLEASE, SORRY.
- **Stability & Cooldown**: Filters out flickering gestures and prevents repeated inputs.
- **GPU/CPU Delegate Switching**: Automatically switches between CPU and GPU for best performance.
- **Sentence Management**: Add letters, delete last character, add spaces, and clear sentence.

## Folder Structure
ISL-Sign-Language-Translator/
├─ app/
│  ├─ src/
│  │  ├─ main/
│  │  │  ├─ java/com/example/signtranslator/
│  │  │  │  ├─ LandmarkAlphabetClassifier.kt
│  │  │  │  ├─ GestureRecognizerHelper.kt
│  │  │  │  ├─ MainActivity.kt
│  │  │  │  ├─ MainViewModel.kt
│  │  │  │  ├─ SentenceManager.kt
│  │  │  │  └─ PredictionResult.kt
│  │  │  ├─ res/
│  │  │  │  ├─ layout/activity_main.xml
│  │  │  │  ├─ drawable/
│  │  │  │  ├─ mipmap/
│  │  │  │  └─ values/
│  │  │  └─ assets/
│  │  │     └─ gesture_recognizer.task
│  │  └─ AndroidManifest.xml
├─ build.gradle
├─ settings.gradle
├─ gradle.properties
├─ local.properties
└─ README.md
## How to Run
1. Clone the repo:
git clone https://github.com/MukundGarg/ISL-Sign-Language-Translator.git

2.	Open in Android Studio (Mac/Windows/Linux).
3.	Build and run on an Android device.
4.	Grant camera permissions when prompted
