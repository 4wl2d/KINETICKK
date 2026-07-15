package void.kinetic.model

import kotlinx.browser.localStorage

actual fun loadMatter(): Int = runCatching {
    localStorage.getItem("kinetic_void_matter")?.toIntOrNull() ?: 0
}.getOrDefault(0)

actual fun saveMatter(value: Int) {
    runCatching { localStorage.setItem("kinetic_void_matter", value.coerceAtLeast(0).toString()) }
}

actual fun loadProgress(): String? = runCatching {
    localStorage.getItem("kinetic_void_progress_v2")
}.getOrNull()

actual fun saveProgress(value: String) {
    runCatching { localStorage.setItem("kinetic_void_progress_v2", value) }
}
