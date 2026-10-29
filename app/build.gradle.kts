plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.github.vexpaer.openlistener"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.vexpaer.openlistener"
        minSdk = 26
        targetSdk = 36
        versionCode = 100
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("stableRelease") {
            System.getenv("ANDROID_KEYSTORE_PATH")?.let { storeFile = file(it) }
            storeType = "pkcs12"
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = "openlistener"
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            if (System.getenv("ANDROID_KEYSTORE_PATH") != null) signingConfig = signingConfigs.getByName("stableRelease")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    sourceSets["main"].jniLibs.srcDir("libs/sherpa/jniLibs")
    sourceSets["main"].java.srcDir("libs/sherpa/kotlin")
    testOptions { animationsDisabled = true }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

android.buildTypes.all {
    buildConfigField("String", "GITHUB_OWNER", "\"vexpaer\"")
    buildConfigField("String", "GITHUB_REPOSITORY", "\"open-listener\"")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:${libs.versions.composeBom.get()}"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.work)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.mlkit.translate)
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("commons-io:commons-io:2.20.0")

    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.android)
    androidTestImplementation(platform("androidx.compose:compose-bom:${libs.versions.composeBom.get()}"))
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
