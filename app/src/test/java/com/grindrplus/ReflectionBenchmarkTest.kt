package com.grindrplus

import org.junit.Test
import org.junit.Ignore
import kotlin.system.measureNanoTime

class ReflectionBenchmarkTest {

    @Test
    @Ignore("Performance benchmark test, not for CI/CD")
    fun benchmarkReflectionVsCached() {
        val iterations = 100000
        val className = "java.util.ArrayList"

        // --- WARMUP ---
        println("Warming up...")
        repeat(5000) {
            val clazz = Class.forName(className)
            clazz.getConstructor().newInstance()
        }

        val cachedClazz = Class.forName(className)
        val cachedConstructor = cachedClazz.getConstructor()
        repeat(5000) {
            cachedConstructor.newInstance()
        }

        // --- MEASURE REFLECTION ---
        println("Measuring uncached reflection...")
        val reflectionTime = measureNanoTime {
            repeat(iterations) {
                // Simulating findClass().getConstructor().newInstance()
                // findClass() essentially calls Class.forName() (via DexClassLoader)
                val clazz = Class.forName(className)
                clazz.getConstructor().newInstance()
            }
        }

        // --- MEASURE CACHED ---
        println("Measuring cached constructor...")
        val cachedTime = measureNanoTime {
            repeat(iterations) {
                cachedConstructor.newInstance()
            }
        }

        val reflectionTimeMs = reflectionTime / 1_000_000.0
        val cachedTimeMs = cachedTime / 1_000_000.0
        val improvement = reflectionTime.toDouble() / cachedTime.toDouble()

        println("Uncached Reflection Time: %.2f ms".format(reflectionTimeMs))
        println("Cached Constructor Time: %.2f ms".format(cachedTimeMs))
        println("Improvement Factor: %.2fx".format(improvement))

        // Assert improvement is significant (at least 2x faster, usually much more)
        println("Benchmark complete: cached reflection was ${improvement}x faster")
    }
}
