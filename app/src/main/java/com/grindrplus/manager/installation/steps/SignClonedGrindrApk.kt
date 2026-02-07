package com.grindrplus.manager.installation.steps

import android.content.Context
import com.grindrplus.manager.installation.BaseStep
import com.grindrplus.manager.installation.Print
import com.grindrplus.manager.utils.KeyStoreUtils
import com.iyxan23.zipalignjava.ZipAlign
import java.io.File
import java.io.RandomAccessFile

class SignClonedGrindrApk(val keyStoreUtils: KeyStoreUtils, val outputDir: File): BaseStep() {
    override suspend fun doExecute(
        context: Context,
        print: Print,
    ) {
        for (file in outputDir.listFiles()!!) {
            if (!file.name.endsWith(".apk")) {
                print("Skipping ${file.name} as it is not an APK")
                continue
            }

            val alignedFile = File(outputDir, "${file.name}-aligned.apk")
            try {
                RandomAccessFile(file, "r").use { zipIn ->
                    alignedFile.outputStream().use { zipOut ->
                        print("Aligning ${file.name}...")
                        ZipAlign.alignZip(zipIn, zipOut)
                    }
                }

                print("Signing ${alignedFile.name}...")
                keyStoreUtils.signApk(alignedFile, File(outputDir, "${file.name}-signed.apk"))
                file.delete()
            } catch (e: Exception) {
                print("Failed to sign ${file.name}: ${e.localizedMessage}")
                throw e
            } finally {
                if (alignedFile.exists()) {
                    alignedFile.delete()
                }
            }
        }
    }

    override val name: String = "Sign cloned Grindr APK"
}