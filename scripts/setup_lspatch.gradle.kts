import java.net.URI
import java.net.URL
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.Comparator

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
