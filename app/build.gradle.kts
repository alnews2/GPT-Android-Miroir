plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "fr.alnews2.gptandroidmiroir"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.alnews2.gptandroidmiroir"
        minSdk = 28
        targetSdk = 36
        // CI injects the release version when building a tagged release.
        versionCode = providers.gradleProperty("releaseVersionCode").orElse("1").get().toInt()
        versionName = providers.gradleProperty("releaseVersionName").orElse("0.1.0").get()
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("com.github.pedroSG94:RTSP-Server:1.3.6")
    // 2.8.1 adds a Camera2 capture-result callback for diagnosing actual sensor exposure and ISO.
    implementation("com.github.pedroSG94.RootEncoder:library:2.8.1")
    testImplementation("junit:junit:4.13.2")
}
