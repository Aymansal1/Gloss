package app.gloss.update

import android.util.Log
import app.gloss.data.compareVersions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** A published build of Gloss on GitHub Releases. */
data class AppRelease(
    val version: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?,
)

/**
 * Checks GitHub Releases for a newer build. Gloss is sideloaded, so this is how people
 * find out there's an update; it asks GitHub at most once every few hours.
 */
class UpdateChecker(
    private val http: OkHttpClient,
    private val currentVersion: String,
    private val repo: String = "Aymansal1/Gloss",
) {
    sealed interface State {
        data object Unknown : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val release: AppRelease) : State
        data class Failed(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Unknown)
    val state: StateFlow<State> = _state.asStateFlow()

    private var lastCheck = 0L

    suspend fun check(force: Boolean = false) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - lastCheck < 4 * 60 * 60 * 1000L && _state.value !is State.Failed) return@withContext
        lastCheck = now
        _state.value = State.Checking
        _state.value = try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            http.newCall(req).execute().use { resp ->
                if (resp.code == 404) return@use State.UpToDate // nothing published yet
                if (!resp.isSuccessful) error("GitHub answered ${resp.code}")
                val release = parseLatest(resp.body?.string().orEmpty()) ?: error("Unreadable release")
                if (isNewer(release.version, currentVersion)) State.Available(release) else State.UpToDate
            }
        } catch (e: Exception) {
            Log.w("UpdateChecker", "Update check failed", e)
            State.Failed(e.message ?: "Couldn't reach GitHub")
        }
    }

    companion object {
        fun parseLatest(json: String): AppRelease? = runCatching {
            val o = JSONObject(json)
            val tag = o.getString("tag_name")
            val assets = o.optJSONArray("assets")
            val apk = (0 until (assets?.length() ?: 0))
                .map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
                ?.optString("browser_download_url")
            AppRelease(
                version = tag.removePrefix("v"),
                notes = o.optString("body"),
                pageUrl = o.optString("html_url"),
                apkUrl = apk,
            )
        }.getOrNull()

        /** True when [remote] is a later version than [current]; local dev builds never nag. */
        fun isNewer(remote: String, current: String): Boolean {
            val cur = current.substringBefore('-').removePrefix("v")
            val rem = remote.substringBefore('-').removePrefix("v")
            if (cur.isBlank() || rem.isBlank()) return false
            return compareVersions(rem, cur) > 0
        }
    }
}
