plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.edurip.bujhechhi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.edurip.bujhechhi"
        minSdk = 24
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0.0"
    }

    signingConfigs {
        // স্থায়ী কী: প্রতিটি বিল্ডে একই সই থাকে, তাই নতুন APK পুরোনোর ওপর আপডেট হয়।
        create("fixed") {
            storeFile = file("bujhechhi.jks")
            storePassword = "bujhechhi123"
            keyAlias = "bujhechhi"
            keyPassword = "bujhechhi123"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("fixed")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/io.netty.versions.properties",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
            )
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")

    // শিক্ষকের ফোনের ভেতরে চলা লোকাল সার্ভার
    implementation("io.ktor:ktor-server-core:3.0.3")
    implementation("io.ktor:ktor-server-cio:3.0.3")
    implementation("io.ktor:ktor-server-websockets:3.0.3")
    implementation("org.slf4j:slf4j-nop:2.0.16")

    // QR কোড
    implementation("com.google.zxing:core:3.5.3")
}
