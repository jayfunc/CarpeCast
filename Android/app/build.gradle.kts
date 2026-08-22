plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jayfunc.carpecast"
    compileSdk = 35

    val gitDate = providers.exec { commandLine("git", "log", "-1", "--format=%cd", "--date=format:%Y-%m-%d %H:%M") }.standardOutput.asText.getOrElse("Unknown").trim()
    val gitHash = providers.exec { commandLine("git", "rev-parse", "--short", "HEAD") }.standardOutput.asText.getOrElse("Unknown").trim()

    defaultConfig {
        applicationId = "com.jayfunc.carpecast"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.1"
        
        buildConfigField("String", "GIT_DATE", "\"${gitDate}\"")
        buildConfigField("String", "GIT_HASH", "\"${gitHash}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.2"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.media:media:1.6.0")

    val composeBom = platform("androidx.compose:compose-bom:2023.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")
}
