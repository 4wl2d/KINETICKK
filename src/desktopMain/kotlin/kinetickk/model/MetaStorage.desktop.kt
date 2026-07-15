package kinetickk.model

import java.util.prefs.Preferences

private val preferences: Preferences by lazy {
    Preferences.userRoot().node("kinetickk/progression")
}

actual fun loadMatter(): Int = runCatching {
    preferences.getInt("kinetickk_matter", 0)
}.getOrDefault(0)

actual fun saveMatter(value: Int) {
    runCatching { preferences.putInt("kinetickk_matter", value.coerceAtLeast(0)) }
}

actual fun loadProgress(): String? = runCatching {
    preferences.get("progress_v2", null)
}.getOrNull()

actual fun saveProgress(value: String) {
    runCatching {
        preferences.put("progress_v2", value)
        preferences.flush()
    }
}
