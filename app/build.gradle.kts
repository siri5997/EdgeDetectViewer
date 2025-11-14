plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.example.edgedetectviewer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.edgedetectviewer"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        ndk {
            // target ABIs to build
            abiFilters += setOf("armeabi-v7a", "arm64-v8a", "x86")
        }

        externalNativeBuild {
            cmake {
                // optional additional flags
                cppFlags += "-std=c++17 -O3"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        // avoid duplicate .so collisions if any
        jniLibs {
            keepDebugSymbols.addAll(listOf("**/*.so"))
        }
    }

    // Use viewBinding if you prefer (optional)
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
}
