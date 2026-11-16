plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

val appVersionCode = (findProperty("versionCode") as String?)?.toIntOrNull()
  ?: System.getenv("VERSION_CODE")?.toIntOrNull()
  ?: 1

val appVersionName = (findProperty("versionName") as String?)
  ?: System.getenv("VERSION_NAME")
  ?: "1.0.0"

val defaultGithubRepo = (findProperty("githubRepo") as String?)
  ?: System.getenv("GITHUB_REPOSITORY")
  ?: "Adrianss31/SwitchPulse"

android {
  namespace = "com.switchpulse.app"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.switchpulse.app"
    minSdk = 26
    targetSdk = 36
    versionCode = appVersionCode
    versionName = appVersionName
    buildConfigField("String", "DEFAULT_GITHUB_REPO", "\"$defaultGithubRepo\"")
  }

  signingConfigs {
    val keystoreFile = file("keystore/switchpulse-release.jks")
    val ksPass = System.getenv("KEYSTORE_PASSWORD")
      ?: (project.findProperty("KEYSTORE_PASSWORD") as? String)
    val kAlias = System.getenv("KEY_ALIAS")
      ?: (project.findProperty("KEY_ALIAS") as? String)
    val kPass = System.getenv("KEY_PASSWORD")
      ?: (project.findProperty("KEY_PASSWORD") as? String)
    if (keystoreFile.exists() && !ksPass.isNullOrBlank() && !kAlias.isNullOrBlank() && !kPass.isNullOrBlank()) {
      create("release") {
        storeFile = keystoreFile
        storePassword = ksPass
        keyAlias = kAlias
        keyPassword = kPass
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.findByName("release")
    }
    debug {
      applicationIdSuffix = ".debug"
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
    buildConfig = true
    aidl = false
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

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons)

  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.glance.appwidget)
  implementation(libs.androidx.glance.material3)
  implementation(libs.androidx.work.runtime.ktx)

  implementation(libs.coil.compose)
  implementation(libs.coil.network.okhttp)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.okhttp)

  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.okhttp.mockwebserver)

  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
}

gradle.taskGraph.whenReady {
  val runningReleaseTask = allTasks.any { task ->
    task.project == project && task.name.contains("Release", ignoreCase = false)
  }
  if (runningReleaseTask && android.signingConfigs.findByName("release") == null) {
    throw GradleException(
      "Release signing is not configured. Provide app/keystore/switchpulse-release.jks and " +
        "KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD env vars or Gradle properties."
    )
  }
}
