package edu.mit.media.mysnapshot.engine

import java.io.File

/**
 * Locates the real bundled `assets/experiment_types.json` from a plain JUnit test (no
 * Robolectric/AGP asset merging available). Unit tests run with the app module directory as
 * the working directory, but this walks upward defensively so it isn't sensitive to exactly
 * how the test task is invoked.
 */
fun readBundledExperimentTypesJson(): String {
    var dir = File(".").absoluteFile
    repeat(4) {
        val fromRepoRoot = File(dir, "app/src/main/assets/experiment_types.json")
        if (fromRepoRoot.exists()) return fromRepoRoot.readText()
        val fromModuleRoot = File(dir, "src/main/assets/experiment_types.json")
        if (fromModuleRoot.exists()) return fromModuleRoot.readText()
        dir = dir.parentFile ?: return@repeat
    }
    error("Could not locate experiment_types.json from working dir ${File(".").absolutePath}")
}
