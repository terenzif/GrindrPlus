plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.googleKsp)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.gpp"
    // libxposed service/interface 102.0.0 require compileSdk ≥ 37 (ADR 0008).
    compileSdk = 37

    defaultConfig {
        // Pack-driven / version-agnostic: no BuildConfig tip destiny for Grindr host versions.
        // Module versionName must NOT embed the Grindr host version (CI/artifacts stay gpp-scoped).
        val gitCommitHash = getGitCommitHash() ?: "unknown"

        // applicationId set per delivery flavor (ADR 0005)
        minSdk = 26
        targetSdk = 34
        versionCode = 14
        versionName = "4.7.2-$gitCommitHash"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    flavorDimensions += "delivery"
    productFlavors {
        create("morphe") {
            dimension = "delivery"
            applicationId = "com.gpp.morphe"
            buildConfigField("String", "DELIVERY_CHANNEL", "\"morphe\"")
        }
        create("alloy") {
            dimension = "delivery"
            applicationId = "com.gpp.alloy"
            buildConfigField("String", "DELIVERY_CHANNEL", "\"alloy\"")
        }
        // Internal slim -m payload (not a primary Releases product). ADR 0005.
        create("embed") {
            dimension = "delivery"
            applicationId = "com.gpp.morphe.payload"
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
        // Manager UI — morphe + alloy only (not slim embed).
        // Register on both java + kotlin dirs so AGP built-in Kotlin picks .kt files.
        getByName("morphe") {
            java.srcDir("src/manager/java")
            kotlin.srcDir("src/manager/java")
            // Rootless Install / Play / LSPatch orchestration
            java.srcDir("src/manager-install/java")
            kotlin.srcDir("src/manager-install/java")
            // Morphe B DEX rewriter (ADR 0007)
            java.srcDir("src/morphe-b/java")
            kotlin.srcDir("src/morphe-b/java")
        }
        getByName("alloy") {
            java.srcDir("src/manager/java")
            kotlin.srcDir("src/manager/java")
            // Vector module runtime + mapping packs
            java.srcDir("src/module/java")
            kotlin.srcDir("src/module/java")
            // Alloy-only Manager↔Vector service bridge
            java.srcDir("src/alloy/java")
            kotlin.srcDir("src/alloy/java")
            assets.srcDir("src/module/assets")
            resources.srcDir("src/module/resources")
        }
        getByName("embed") {
            // Slim LSPatch -m payload: module runtime only
            java.srcDir("src/module/java")
            kotlin.srcDir("src/module/java")
            assets.srcDir("src/module/assets")
            resources.srcDir("src/module/resources")
        }
        // Unit tests cover Morphe B without a product flavor on the test classpath.
        getByName("test") {
            java.srcDir("src/morphe-b/java")
            kotlin.srcDir("src/morphe-b/java")
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
    // Legacy LSPosed stubs removed — Alloy/embed use libxposed API 102 (ADR 0008).
    // Do not compileOnly(fileTree libs/*.jar): that pulled LSPosed-api + fat lspatch into all flavors.
    compileOnly(libs.bcprov.jdk18on)
    "alloyCompileOnly"(libs.libxposed.api)
    "embedCompileOnly"(libs.libxposed.api)
    "alloyImplementation"(libs.libxposed.service)

    // Compose compiler plugin is applied project-wide; keep minimal runtime on all flavors.
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.runtime.android)

    // DexKit: packaged on alloy only (ADR 0006)
    "alloyImplementation"(libs.dexkit)
    "morpheCompileOnly"(libs.dexkit)
    "embedCompileOnly"(libs.dexkit)

    // dexlib2 rewriter for Morphe B (ADR 0007) — Morphe Manager only.
    "morpheImplementation"(libs.smali.dexlib2)
    "alloyCompileOnly"(libs.smali.dexlib2)
    "embedCompileOnly"(libs.smali.dexlib2)

    // LSPatch jar: packaged on morphe Manager only (ADR 0005).
    // Fat jar embeds checker-qual — drop the Maven copy on Morphe to avoid dex duplicates.
    "morpheImplementation"(fileTree("libs") { include("lspatch.jar") })
    "alloyCompileOnly"(fileTree("libs") { include("lspatch.jar") })
    "embedCompileOnly"(fileTree("libs") { include("lspatch.jar") })
    // lspatch.jar is a fat jar; drop overlapping Maven jars on Morphe dex merge.
    // Do not apply to *UnitTest* / *AndroidTest* — Robolectric needs ListenableFuture.
    configurations.configureEach {
        val n = name
        if (n.startsWith("morphe", ignoreCase = true) &&
            !n.contains("UnitTest", ignoreCase = true) &&
            !n.contains("AndroidTest", ignoreCase = true)
        ) {
            exclude(group = "org.checkerframework", module = "checker-qual")
            exclude(group = "com.google.code.findbugs", module = "jsr305")
            exclude(group = "org.jetbrains", module = "annotations")
            exclude(group = "com.google.guava", module = "guava")
            exclude(group = "com.google.guava", module = "failureaccess")
            exclude(group = "com.google.guava", module = "listenablefuture")
            exclude(group = "com.google.j2objc", module = "j2objc-annotations")
            exclude(group = "com.google.errorprone", module = "error_prone_annotations")
            exclude(group = "org.codehaus.mojo", module = "animal-sniffer-annotations")
        }
    }

    // Install / Play download tooling — Morphe only (Alloy has no Install tab)
    "morpheImplementation"(libs.gplayapi) {
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
    "morpheImplementation"(libs.fetch2okhttp)
    "morpheImplementation"(libs.rootbeer.lib)
    "alloyImplementation"(libs.rootbeer.lib)
    "morpheImplementation"(libs.zip.android) {
        artifact { type = "aar" }
    }
    "morpheImplementation"(libs.zipalign.java)
    "morpheImplementation"(libs.arsclib)

    testImplementation(libs.smali.dexlib2)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testCompileOnly(libs.libxposed.api)
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
        println("VERSION_INFO: GrindMod / Grindr++ v$versionName")
    }
}

base {
    val versionName = android.defaultConfig.versionName
    val sanitizedVersionName = (versionName ?: "").replace(Regex("[^a-zA-Z0-9._-]"), "_").trim('_')
    archivesName.set("gpp_v${sanitizedVersionName}")
}
