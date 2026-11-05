plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.example"
  compileSdk = 36
  defaultConfig {
    applicationId = "com.aistudio.lenochat.xkdfzq"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
    buildConfigField("String", "FIREBASE_API_KEY", "\"AIzaSyB9oI7Amr9C8WqUBgpettT6UA23d89qMao\"")
    buildConfigField("String", "FIREBASE_PROJECT_ID", "\"leno-9235f\"")
    buildConfigField("String", "FIREBASE_APP_ID", "\"1:92383670612:android:23fc556e98ea1440a498c1\"")
    buildConfigField("String", "FIREBASE_MESSAGING_SENDER_ID", "\"92383670612\"")
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = true
    shaders = false
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation("androidx.compose.material:material-icons-extended")
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
  implementation("androidx.navigation:navigation-compose:2.8.8")

  // Firebase
  implementation(platform("com.google.firebase:firebase-bom:33.10.0"))
  implementation("com.google.firebase:firebase-auth")
  implementation("com.google.firebase:firebase-firestore")
  implementation("com.google.firebase:firebase-storage")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

  // Room
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)

  // Image loading
  implementation("io.coil-kt:coil-compose:2.7.0")

  // RecyclerView
  implementation("androidx.recyclerview:recyclerview:1.4.0")
}
