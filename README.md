🟦 EdgeDetect Viewer — Android + Web Viewer

A hybrid computer-vision project demonstrating:

📷 Real-time camera frame capture (Camera2 API)

⚙️ Native image processing using C++ (JNI + NDK) + OpenCV

🎨 Rendering with OpenGL ES 2.0

🌐 A simple Web Viewer (HTML + TypeScript) to display processed frames

🚀 Features

Android App

Live camera feed using Camera2

Frame conversion YUV → NV21 → OpenCV Mat

C++ native processing (Canny / grayscale / custom filters)

Outputs RGBA int[] back to Kotlin/Java

Real-time rendering using GLSurfaceView

Orientation-correct rendering

Modular CMake + OpenCV integration

Web Viewer

Static demo viewer built with:

index.html

style.css

src/main.ts → compiled to dist/main.js

Shows:

Processed sample frame

FPS counter (simulated)

Resolution details

No frameworks needed — runs in any browser

📁 Project Structure

EdgeDetectViewer/
│

├── app/

│   ├── src/main/java/...         # MainActivity, renderer, camera code

│   ├── src/main/cpp/native-lib.cpp
│   ├── src/main/cpp/opencv/      # (ignored in Git) OpenCV .so
│   ├── src/main/cpp/llvm-libc++/ # (ignored in Git) libc++_shared.so
│   ├── src/main/res/layout/activity_main.xml
│
├── web-viewer/
│   ├── index.html
│   ├── style.css
│   ├── src/main.ts
│   ├── dist/main.js
│   ├── images/processed_frame.png
│   └── README.md (Web viewer only)
│
├── README.md (This file)
└── .gitignore

Setup Instructions

1. Install required tools

In Android Studio → SDK Manager → SDK Tools:

✔ NDK (Side by side)
✔ CMake
✔ LLDB
✔ Android SDK Platform 34

2. Add OpenCV + libc++_shared.so

Place your native libraries here:

app/src/main/jniLibs/arm64-v8a/
app/src/main/jniLibs/armeabi-v7a/
app/src/main/jniLibs/x86/
app/src/main/jniLibs/x86_64/

⚠️ These .so files are ignored in Git using .gitignore

3. Update your CMakeLists.txt

add_library(opencv_java4 SHARED IMPORTED)
set_target_properties(opencv_java4 PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/opencv/libopencv_java4.so)

add_library(cpp_shared SHARED IMPORTED)
set_target_properties(cpp_shared PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/llvm-libc++/libc++_shared.so)

target_link_libraries(
        native-lib
        opencv_java4
        cpp_shared
        log
)

🧠 Architecture Overview

Android Frame Flow

Camera2 API
   ↓
ImageReader (YUV_420_888)
   ↓ convert
NV21 ByteArray
   ↓ JNI
native-lib.cpp (C++)
   ↓ OpenCV processing
RGBA int[]
   ↓
GLSurfaceView Renderer
   ↓
Final on-screen frame

JNI Bridge

Kotlin ↔ C++

Converts NV21 → Mat

Runs OpenCV processing

Returns RGBA pixels

OpenGL Rendering

Upload frame as texture

Fullscreen quad drawing

Rotation matrix fixes orientation

Web Viewer Architecture

index.html        # UI

style.css         # Basic styling

src/main.ts       # TypeScript logic

dist/main.js      # Compiled JS

images/frame.png  # Sample processed image


Build with:

cd web-viewer

npm install

npm run build


Run:

Open web-viewer/index.html

🖼️ Screenshots 

📸 Android App (Edge Detection Running)
![9e46314d-5662-4936-84d8-24fded35efe1](https://github.com/user-attachments/assets/a8161884-7f08-454a-b5c6-e6035ccfef50)

🌐 Web Viewer Screenshot
<img width="1900" height="962" alt="image" src="https://github.com/user-attachments/assets/cdcbb358-b9f4-4758-b294-8a970e44c95c" />

