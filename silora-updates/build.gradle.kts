plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "org.siloserver.silo.updates"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }
    buildFeatures {
        aidl = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.media3.session)
    constraints {
        // Match the app's AndroidX versions when building this library on its own.
        implementation("androidx.core:core:1.16.0")
        implementation("androidx.annotation:annotation-experimental:1.5.1")
        implementation("androidx.lifecycle:lifecycle-service:${libs.versions.lifecycle.asProvider().get()}")
        implementation("androidx.lifecycle:lifecycle-livedata:${libs.versions.lifecycle.asProvider().get()}")
        implementation("androidx.room:room-runtime:${libs.versions.room.get()}")
        implementation(libs.androidx.profileinstaller)
    }
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    testImplementation(libs.junit4)
}
