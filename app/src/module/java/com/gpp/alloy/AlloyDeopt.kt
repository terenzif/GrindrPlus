package com.gpp.alloy

import com.gpp.GppXposed
import com.gpp.core.LogSource
import com.gpp.core.Logger
import java.lang.reflect.Executable
import java.lang.reflect.Method

/**
 * Deoptimize callers so short hooked callees are not skipped due to inline (API 102 + DexKit).
 */
object AlloyDeopt {
    fun deoptimize(executable: Executable): Boolean = GppXposed.deoptimize(executable)

    /**
     * Best-effort: deoptimize [callers] found via DexKit (or manual) for a hooked [callee].
     */
    fun deoptimizeCallers(callee: Method, callers: Collection<Executable>): Int {
        var ok = 0
        for (caller in callers) {
            if (deoptimize(caller)) {
                ok++
                Logger.d(
                    "deoptimized caller ${caller.declaringClass.name}#${caller.name} for ${callee.name}",
                    LogSource.MODULE,
                )
            }
        }
        return ok
    }
}
