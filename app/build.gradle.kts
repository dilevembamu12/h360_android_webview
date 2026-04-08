plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "cd.h360.pos"
    compileSdk = 35

    defaultConfig {
        applicationId = "cd.h360.pos"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // H360-CUSTOM-PATCH [H360_ANDROID_WEBVIEW_URL]
        // Default values (safe baseline). buildTypes below can override per variant.
        buildConfigField("String", "WEBVIEW_BASE_URL", "\"https://pos.h360.cd/login\"")
        buildConfigField("String", "ALLOWED_INTERNAL_HOSTS", "\"pos.h360.cd\"")
        buildConfigField("int", "SPLASH_DELAY_MS", "1200")
        buildConfigField("boolean", "ENABLE_KIOSK_MODE", "false")
        buildConfigField("String", "MAINTENANCE_CHECK_URL", "\"https://pos.h360.cd/h360offline/ping\"")
        buildConfigField("String", "WIDGET_INSIGHTS_URL", "\"https://pos.h360.cd/h360/widgets/insights\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            // Production release must always target the public domain.
            buildConfigField("String", "WEBVIEW_BASE_URL", "\"https://pos.h360.cd/login\"")
            buildConfigField("String", "ALLOWED_INTERNAL_HOSTS", "\"pos.h360.cd\"")
            buildConfigField("String", "MAINTENANCE_CHECK_URL", "\"https://pos.h360.cd/h360offline/ping\"")
            buildConfigField("String", "WIDGET_INSIGHTS_URL", "\"https://pos.h360.cd/h360/widgets/insights\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            // Debug remains on stack for internal testing.
            buildConfigField("String", "WEBVIEW_BASE_URL", "\"https://stack.git.h360.cd/login\"")
            buildConfigField("String", "ALLOWED_INTERNAL_HOSTS", "\"pos.h360.cd,stack.git.h360.cd\"")
            buildConfigField("String", "MAINTENANCE_CHECK_URL", "\"https://stack.git.h360.cd/h360offline/ping\"")
            buildConfigField("String", "WIDGET_INSIGHTS_URL", "\"https://stack.git.h360.cd/h360/widgets/insights\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        // H360-CUSTOM-PATCH [H360_ANDROID_WEBVIEW_BUILD]
        // Required because we define custom buildConfigField entries.
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
}
