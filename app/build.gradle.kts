plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.googleKsp)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.grindrplus"
    compileSdk = 35

    defaultConfig {
        // Supported hook target (see supported_target.json). Not the same as Play scrape.
        val grindrVersionName = listOf("26.16.1")
        val grindrVersionCode = listOf(179451)
        val gitCommitHash = getGitCommitHash() ?: "unknown"

        // applicationId set per delivery flavor (ADR 0005)
        minSdk = 26
        targetSdk = 34
        versionCode = 14
        versionName = "4.7.2-${grindrVersionName.let { it.joinToString("_") }}_$gitCommitHash"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField(
            "String[]",
            "TARGET_GRINDR_VERSION_NAMES",
            grindrVersionName.let { it.joinToString(prefix = "{", separator = ", ", postfix = "}") { version -> "\"$version\"" } }
        )

        buildConfigField(
            "int[]",
            "TARGET_GRINDR_VERSION_CODES",
            grindrVersionCode.let { it.joinToString(prefix = "{", separator = ", ", postfix = "}") { code -> "$code" } }
        )
    }

    flavorDimensions += "delivery"
    productFlavors {
        create("morphe") {
            dimension = "delivery"
            applicationId = "com.grindrplus.morphe"
            buildConfigField("String", "DELIVERY_CHANNEL", "\"morphe\"")
        }
        create("alloy") {
            dimension = "delivery"
            applicationId = "com.grindrplus.alloy"
            buildConfigField("String", "DELIVERY_CHANNEL", "\"alloy\"")
        }
        // Internal slim -m payload (not a primary Releases product). ADR 0005.
        create("embed") {
            dimension = "delivery"
            applicationId = "com.grindrplus.morphe.payload"
            buildConfigField("String", "DELIVERY_CHANNEL", "\"embed\"")
        }
    }

    buildFeatures {
        buildConfig = true
        aidl = true
        compose = true
    }

    buildTypes {
        release {
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        // Manager UI / Install / LSPatch orchestration — not shipped in slim embed payload.
        getByName("morphe") {
            java.srcDir("src/manager/java")
        }
        getByName("alloy") {
            java.srcDir("src/manager/java")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Shared module / hooks payload (all flavors including slim embed)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.coordinatorlayout)
    implementation(libs.material)
    implementation(libs.square.okhttp)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.timber)
    compileOnly(fileTree("libs") { include("*.jar") })
    compileOnly(libs.bcprov.jdk18on)

    // Compose compiler plugin is applied project-wide; keep minimal runtime on all flavors.
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.runtime.android)

    // DexKit: packaged on alloy only (ADR 0006)
    "alloyImplementation"(libs.dexkit)
    "morpheCompileOnly"(libs.dexkit)
    "embedCompileOnly"(libs.dexkit)

    // dexlib2 rewriter for Morphe B (ADR 0007). Packaged on Manager channels only;
    // slim embed stays compileOnly so the Vector payload does not grow.
    "morpheImplementation"(libs.smali.dexlib2)
    "alloyImplementation"(libs.smali.dexlib2)
    "embedCompileOnly"(libs.smali.dexlib2)

    // LSPatch jar: packaged on morphe Manager only (ADR 0005)
    "morpheImplementation"(fileTree("libs") { include("lspatch.jar") })
    "alloyCompileOnly"(fileTree("libs") { include("lspatch.jar") })
    "embedCompileOnly"(fileTree("libs") { include("lspatch.jar") })

    // Manager UI / Install tooling — morphe + alloy only (not slim embed)
    "morpheImplementation"(libs.gplayapi) {
        exclude(group = "com.google.code.gson", module = "gson")
    }
    "alloyImplementation"(libs.gplayapi) {
        exclude(group = "com.google.code.gson", module = "gson")
    }

    val managerComposeBom = platform(libs.compose.bom)
    "morpheImplementation"(managerComposeBom)
    "alloyImplementation"(managerComposeBom)
    "morpheImplementation"(libs.androidx.material3)
    "alloyImplementation"(libs.androidx.material3)
    "morpheImplementation"(libs.androidx.ui.tooling.preview)
    "alloyImplementation"(libs.androidx.ui.tooling.preview)
    "morpheImplementation"(libs.androidx.ui.tooling)
    "alloyImplementation"(libs.androidx.ui.tooling)
    "morpheImplementation"(libs.androidx.material.icons.core)
    "alloyImplementation"(libs.androidx.material.icons.core)
    "morpheImplementation"(libs.androidx.material.icons.extended)
    "alloyImplementation"(libs.androidx.material.icons.extended)
    "morpheImplementation"(libs.androidx.activity.compose)
    "alloyImplementation"(libs.androidx.activity.compose)
    "morpheImplementation"(libs.androidx.navigation.compose)
    "alloyImplementation"(libs.androidx.navigation.compose)
    "morpheImplementation"(libs.coil.compose)
    "alloyImplementation"(libs.coil.compose)
    "morpheImplementation"(libs.coil.network.okhttp)
    "alloyImplementation"(libs.coil.network.okhttp)
    "morpheImplementation"(libs.coil.gif)
    "alloyImplementation"(libs.coil.gif)
    "morpheImplementation"(libs.compose.markdown)
    "alloyImplementation"(libs.compose.markdown)
    "morpheImplementation"(libs.plausible.android.sdk)
    "alloyImplementation"(libs.plausible.android.sdk)
    "morpheImplementation"(libs.fetch2)
    "alloyImplementation"(libs.fetch2)
    "morpheImplementation"(libs.fetch2okhttp)
    "alloyImplementation"(libs.fetch2okhttp)
    "morpheImplementation"(libs.rootbeer.lib)
    "alloyImplementation"(libs.rootbeer.lib)
    "morpheImplementation"(libs.zip.android) {
        artifact { type = "aar" }
    }
    "alloyImplementation"(libs.zip.android) {
        artifact { type = "aar" }
    }
    "morpheImplementation"(libs.zipalign.java)
    "alloyImplementation"(libs.zipalign.java)
    "morpheImplementation"(libs.arsclib)
    "alloyImplementation"(libs.arsclib)

    testImplementation(libs.smali.dexlib2)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(fileTree("libs") { include("*.jar") })
}

apply(from = rootProject.file("scripts/setup_lspatch.gradle.kts"))

fun getGitCommitHash(): String? {
    return try {
        val isGitRepo = providers.exec {
            commandLine("git", "rev-parse", "--is-inside-work-tree")
            isIgnoreExitValue = true
        }.result.get().exitValue == 0

        if (isGitRepo) {
            providers.exec {
                commandLine("git", "rev-parse", "--short", "HEAD")
            }.standardOutput.asText.get().trim()
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

tasks.register("printVersionInfo") {
    doLast {
        val versionName = android.defaultConfig.versionName
        println("VERSION_INFO: GrindrPlus v$versionName")
    }
}

base {
    val versionName = android.defaultConfig.versionName
    val sanitizedVersionName = (versionName ?: "").replace(Regex("[^a-zA-Z0-9._-]"), "_").trim('_')
    archivesName.set("GrindrPlus_v${sanitizedVersionName}")
}
