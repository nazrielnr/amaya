plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

import java.util.Properties

val keystorePropertiesFile = rootProject.file(".env.local")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.amaya.intelligence"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.amaya.intelligence"
        minSdk = 26
        // Sengaja 28: Android 10+ (API >= 29) menegakkan kebijakan W^X yang melarang
        // exec() biner dari direktori data aplikasi. PRoot + shell Alpine (busybox)
        // dieksekusi dari filesDir, jadi targetSdk harus tetap di bawah 29 agar
        // sandbox Linux tetap berfungsi (pola yang sama dipakai UserLAnd/Termux).
        targetSdk = 28
        versionCode = 5
        versionName = "1.0.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Room schema export for migrations
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    signingConfigs {
        create("debugConfig") {
            // debug.keystore sengaja TIDAK di-commit (.gitignore: *.keystore),
            // sehingga CI tidak memilikinya. Urutan fallback:
            //   1. debug.keystore milik proyek (jika developer memilikinya)
            //   2. debug keystore bawaan Android SDK (~/.android/debug.keystore)
            //   3. keystore di build/, dibuat task ensureDebugKeystore pada
            //      execution time (bukan configuration time)
            val projectKeystore = file("${rootDir}/debug.keystore")
            val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
            val defaultKeystore = androidHome?.let { file("$it/.android/debug.keystore") }
                ?: file("${System.getProperty("user.home")}/.android/debug.keystore")
            val generatedKeystore = layout.buildDirectory.file("generated/debug.keystore").get().asFile

            val resolved = when {
                projectKeystore.exists() -> projectKeystore
                defaultKeystore.exists() -> defaultKeystore
                else -> generatedKeystore
            }

            storeFile = resolved
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }

        create("release") {
            if (keystoreProperties.containsKey("AMAYA_KEYSTORE_PASSWORD")) {
                storeFile = file("../release.keystore")
                storePassword = keystoreProperties["AMAYA_KEYSTORE_PASSWORD"] as String
                keyAlias = keystoreProperties["AMAYA_KEY_ALIAS"] as String
                keyPassword = keystoreProperties["AMAYA_KEYSTORE_PASSWORD"] as String
            }
        }
    }

    // Fallback terakhir: hasilkan debug keystore bila runner tidak punya
    // ~/.android/debug.keystore. Dijalankan pada execution time karena Gradle
    // melarang proses eksternal saat configuration time.
    val ensureDebugKeystore = tasks.register("ensureDebugKeystore") {
        val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        val defaultKeystore = androidHome?.let { file("$it/.android/debug.keystore") }
            ?: file("${System.getProperty("user.home")}/.android/debug.keystore")
        val projectKeystore = file("${rootDir}/debug.keystore")
        val generatedKeystore = layout.buildDirectory.file("generated/debug.keystore").get().asFile

        onlyIf {
            !projectKeystore.exists() && !defaultKeystore.exists() && !generatedKeystore.exists()
        }

        doLast {
            generatedKeystore.parentFile.mkdirs()
            val keytool = System.getenv("JAVA_HOME")?.let { "$it/bin/keytool" } ?: "keytool"
            val proc = ProcessBuilder(
                keytool,
                "-genkeypair", "-v",
                "-keystore", generatedKeystore.absolutePath,
                "-storepass", "android",
                "-keypass", "android",
                "-alias", "androiddebugkey",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "10950",
                "-dname", "CN=Android Debug, OU=Debug, O=Android, L=Unknown, ST=Unknown, C=US"
            ).redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText()
            if (proc.waitFor() != 0) {
                throw GradleException("Gagal membuat debug keystore:\n$out")
            }
            println("debug keystore dibuat di " + generatedKeystore.absolutePath)
        }
    }

    // Task Android baru terdaftar setelah blok android{} dievaluasi.
    afterEvaluate {
        tasks.matching {
            it.name.startsWith("validateSigning") ||
                it.name.startsWith("packageDebug") || it.name.startsWith("packagePerf")
        }.configureEach {
            dependsOn(ensureDebugKeystore)
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            // Tetap hasilkan APK universal agar bisa dipasang di perangkat apa pun.
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystoreProperties.containsKey("AMAYA_KEYSTORE_PASSWORD")) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debugConfig")
        }

        // Varian installable mirip-produksi untuk pengujian performa di setiap push CI:
        // jalur kode release + R8, tapi debug signing (dan applicationId `.perf`) sehingga
        // bisa di-sideload tanpa secrets release keystore dan terpasang berdampingan
        // dengan build debug/release. Bukan untuk distribusi.
        create("perf") {
            initWith(buildTypes.getByName("release"))
            applicationIdSuffix = ".perf"
            signingConfig = signingConfigs.getByName("debugConfig")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // targetSdk 28 di atas adalah keputusan disengaja (kebijakan exec W^X sandbox
    // Linux, pola UserLAnd/Termux) dan aplikasi didistribusikan di luar Google Play
    // (sideload / GitHub Releases), jadi cek lint ketat-Play ini dinonaktifkan.
    lint {
        disable += "ExpiredTargetSdkVersion"
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt"
            )
        }
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Networking - Retrofit + OkHttp
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.okhttp.logging)
    implementation(libs.okhttp.sse)

    // Moshi JSON
    implementation(libs.moshi.core)
    ksp(libs.moshi.codegen)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // SVG file-type icons
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    // Hilt DI
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // WorkManager (for background reminder AI reply)
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Security - Encrypted SharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Apache Commons Compress for tar.gz extraction
    implementation("org.apache.commons:commons-compress:1.26.0")
    // XZ compression library for .tar.xz (required by Commons Compress)
    implementation("org.tukaani:xz:1.9")

    // WebSocket client for Remote Session (Antigravity IDE bridge)
    implementation("org.java-websocket:Java-WebSocket:1.5.7")

    // QR Scanning - CameraX + ML Kit (Unbundled Play Services)
    val cameraxVersion = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
    implementation("com.google.guava:guava:33.3.1-android")

    // Custom Tabs for Codex OAuth browser flow
    implementation("androidx.browser:browser:1.8.0")

    // GeckoView browser engine.
    implementation("org.mozilla.geckoview:geckoview-omni:152.0.20260713164047") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }

    // Testing
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
