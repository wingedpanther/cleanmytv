plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.wingedpanther.cleanmytv.home"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.wingedpanther.cleanmytv.home"
        // Android 9: the Mi Box S and nearly every TV still in use.
        minSdk = 26
        targetSdk = 36
        versionCode = 56
        versionName = "1.13.18"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Local runs fall back to the debug key; scripts/build-home.ts signs with the app's own key
            // (android.injected.signing.*), so every copy of the desktop app installs over the others.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    // The tests check Home's English text, whatever language this computer runs in.
    testOptions { unitTests.all { it.systemProperty("user.language", "en"); it.systemProperty("user.country", "US") } }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.tv:tv-material:1.1.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    // Drawn icons for the quick-settings bar; R8 keeps only the few used.
    implementation("androidx.compose.material:material-icons-extended")
    // The QR code that sends a phone to Home's upload page; only the encoder is used.
    implementation("com.google.zxing:core:3.5.3")
    testImplementation("junit:junit:4.13.2")
    // Android's org.json is a stub on the JVM; the real one parses the weather in tests.
    testImplementation("org.json:json:20240303")
    // Android's ICU is a stub on the JVM too; ICU4J has the same plural rules for the i18n tests.
    testImplementation("com.ibm.icu:icu4j:77.1")
}
