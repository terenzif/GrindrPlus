import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.net.URI
import java.net.URL
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.Comparator

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.googleKsp)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.grindrplus"
    compileSdk = 35

    defaultConfig {
        val grindrVersionName = listOf("25.20.0")
        val grindrVersionCode = listOf(147239)
        val gitCommitHash = getGitCommitHash() ?: "unknown"

        applicationId = "com.grindrplus"
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
    
    // Replacement for applicationVariants logic
    defaultConfig {
        // archivesBaseName set via base {} block below
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.coordinatorlayout)
    implementation(libs.material)
    implementation(libs.square.okhttp)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.runtime.android)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    compileOnly(fileTree("libs") { include("*.jar") })
    implementation(fileTree("libs") { include("lspatch.jar") })

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)

    implementation(libs.androidx.material3)

    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.compose.markdown)
    implementation(libs.plausible.android.sdk)
    implementation(libs.timber)
    implementation(libs.fetch2)
    implementation(libs.fetch2okhttp)
    implementation(libs.rootbeer.lib)
    implementation(libs.zip.android) {
        artifact {
            type = "aar"
        }
    }
    implementation(libs.zipalign.java)
    implementation(libs.coil.gif)
    implementation(libs.arsclib)
    compileOnly(libs.bcprov.jdk18on)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}

tasks.register("setupLSPatch") {
    doLast {
        val tempDir = layout.buildDirectory.get().asFile.resolve("lspatch_temp")
        tempDir.mkdirs()

        val nightlyLink = "https://nightly.link/JingMatrix/LSPatch/workflows/main/master?preview"
        // Simple download of the nightly page to find the URL
        val nightlyContent = URI(nightlyLink).toURL().readText()
        val jarUrl = Regex("https:\\/\\/nightly\\.link\\/JingMatrix\\/LSPatch\\/workflows\\/main\\/master\\/lspatch-debug-[^.]+\\.zip").find(
            nightlyContent
        )!!.value

        val zipFile = tempDir.resolve("lspatch.zip")
        
        // Download zip
        URI(jarUrl).toURL().openStream().use { input ->
            FileOutputStream(zipFile).use { output ->
                input.copyTo(output)
            }
        }

        // Unzip
        copy {
            from(zipTree(zipFile))
            into(tempDir)
        }

        val jarFile = tempDir.listFiles()?.find { it.name.contains("jar-") && it.extension == "jar" }
            ?: throw GradleException("LSPatch jar not found in zip")

        // Extract assets/lspatch/so* to src/main/
        copy {
            from(zipTree(jarFile)) {
                include("assets/lspatch/so*")
            }
            into(project.projectDir.resolve("src/main/"))
        }

        // Move/Copy jar to libs/lspatch.jar
        val targetLib = project.projectDir.resolve("libs/lspatch.jar")
        targetLib.parentFile.mkdirs()
        jarFile.copyTo(targetLib, overwrite = true)

        // Delete specific classes/packages from the jar using ZipFileSystem
        val jarUri = URI.create("jar:" + targetLib.toURI())
        val env = mapOf("create" to "false")
        
        FileSystems.newFileSystem(jarUri, env).use { fs ->
            // Delete com/google/common/util/concurrent/ListenableFuture.class
            val p1 = fs.getPath("com/google/common/util/concurrent/ListenableFuture.class")
            if (Files.exists(p1)) {
                Files.delete(p1)
            }

            // Delete com/google/errorprone/annotations/*
            val p2 = fs.getPath("com/google/errorprone/annotations")
            if (Files.exists(p2)) {
                 Files.walk(p2)
                    .sorted(Comparator.reverseOrder())
                    .forEach { p ->
                         Files.delete(p)
                    }
            }
        }
    }
}

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
    archivesName.set("GPlus_v${sanitizedVersionName}")
}
