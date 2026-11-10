import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Release signing: keystore.properties (storeFile, storePassword, keyAlias, keyPassword) when
// present, otherwise the local debug key, so `assembleRelease` always gives an installable APK.
val keystoreProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

// Optional assistant key from local.properties (never committed), so it need not be typed on the
// head unit: ASSISTANT_PROVIDER=anthropic, ASSISTANT_API_KEY=..., ASSISTANT_MODEL=... . The key
// ends up inside the APK: keep such builds to yourself.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun localProperty(name: String): String =
    localProperties.getProperty(name).orEmpty().trim().replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "io.github.giannoccarol.corollalauncher"
    // Compiled against the newest SDK AndroidX requires; minSdk decides what it runs on.
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "io.github.giannoccarol.corollalauncher"
        // Cheap Android head units often run Android 8-10 while advertising a newer version.
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"

        buildConfigField("String", "ASSISTANT_PROVIDER", "\"${localProperty("ASSISTANT_PROVIDER")}\"")
        buildConfigField("String", "ASSISTANT_API_KEY", "\"${localProperty("ASSISTANT_API_KEY")}\"")
        buildConfigField("String", "ASSISTANT_MODEL", "\"${localProperty("ASSISTANT_MODEL")}\"")
    }

    signingConfigs {
        if (!keystoreProperties.isEmpty) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    lint {
        // Translations are checked by scripts/check_translations.py, which also catches extras.
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)

    testImplementation(libs.junit4)
    testImplementation(libs.org.json)
}
