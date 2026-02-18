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
.
├── app
│   ├── build.gradle
│   └── src
│       └── main
├── build.gradle
├── folder_structure.txt
├── gradle
│   ├── gradle-daemon-jvm.properties
│   └── wrapper
│       └── gradle-wrapper.properties
├── gradle.properties
├── local.properties
├── README.md
└── settings.gradle
## How to Run
1. Clone the repo:
git clone https://github.com/MukundGarg/ISL-Sign-Language-Translator.git

2.	Open in Android Studio (Mac/Windows/Linux).
3.	Build and run on an Android device.
4.	Grant camera permissions when prompted
