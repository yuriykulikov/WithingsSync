plugins {
  id("com.android.application")
  kotlin("android")
  id("kotlinx-serialization")
  id("de.mannodermaus.android-junit5")
}

val localProperties =
    project.rootProject.file("local.properties").takeIf { it.exists() }?.readLines().orEmpty()

fun localPropertyOrEnv(name: String): String {
  return localProperties.firstOrNull { it.startsWith(name) }?.substringAfter("=")
      ?: System.getenv()["CLIENT_ID"] ?: ""
}

android {
  compileSdk = 33
  defaultConfig {
    applicationId = "com.github.yuriykulikov.withingssync"
    minSdk = 28
    targetSdk = 33
    versionCode = 10500
    versionName = "1.5.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    manifestPlaceholders["appAuthRedirectScheme"] = "com.github.yuriykulikov.withingssync"
  }

  buildTypes {
    getByName("debug") {
      isTestCoverageEnabled = true
      isMinifyEnabled = false
      buildConfigField("String", "CLIENT_ID", "\"${localPropertyOrEnv("CLIENT_ID")}\"")
      buildConfigField("String", "CLIENT_SECRET", "\"${localPropertyOrEnv("CLIENT_SECRET")}\"")
      buildConfigField("String", "ACRA_EMAIL", "\"${localPropertyOrEnv("ACRA_EMAIL")}\"")
    }
    getByName("release") {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.txt")
      buildConfigField("String", "CLIENT_ID", "\"${localPropertyOrEnv("CLIENT_ID")}\"")
      buildConfigField("String", "CLIENT_SECRET", "\"${localPropertyOrEnv("CLIENT_SECRET")}\"")
      buildConfigField("String", "ACRA_EMAIL", "\"${localPropertyOrEnv("ACRA_EMAIL")}\"")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  kotlinOptions { jvmTarget = "1.8" }

  buildFeatures { compose = true }

  composeOptions { kotlinCompilerExtensionVersion = "1.3.0" }
}

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-stdlib:${project.extra["kotlin"]}")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.0")
  implementation("androidx.appcompat:appcompat:1.5.1")
  implementation("org.slf4j:slf4j-api:1.7.36")
  implementation("com.github.tony19:logback-android:2.0.0")
  val ktor_version = "2.1.0"
  implementation("io.ktor:ktor-client-core:$ktor_version")
  implementation("io.ktor:ktor-client-cio:$ktor_version")
  implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
  implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
  implementation("net.openid:appauth:0.11.1")
  implementation("androidx.datastore:datastore-preferences:1.0.0")

  val koin_version = "3.2.1"
  implementation("io.insert-koin:koin-core:$koin_version")
  implementation("io.insert-koin:koin-android:$koin_version")
  implementation("io.insert-koin:koin-androidx-workmanager:$koin_version")

  implementation("org.slf4j:slf4j-api:1.7.36")
  implementation("com.github.tony19:logback-android:2.0.0")
  implementation("ch.acra:acra-mail:5.9.3")
}

dependencies {
  implementation("androidx.compose.ui:ui:1.2.1")
  // Tooling support (Previews, etc.)
  implementation("androidx.compose.ui:ui-tooling:1.2.1")
  // Foundation (Border, Background, Box, Image, Scroll, shapes, animations, etc.)
  implementation("androidx.compose.foundation:foundation:1.2.1")
  // Material Design
  implementation("androidx.compose.material:material:1.2.1")
  // Material design icons
  implementation("androidx.compose.material:material-icons-core:1.2.1")
  implementation("androidx.compose.material:material-icons-extended:1.2.1")
  // Activity
  implementation("androidx.activity:activity-compose:1.5.1")
  // UI Tests
  androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.2.1")
}

// test
dependencies {
  testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.0")
  testImplementation("io.strikt:strikt-core:0.34.1")
  testImplementation("io.strikt:strikt-jvm:0.34.1")

  androidTestImplementation("androidx.test.ext:junit:1.1.3")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
}
