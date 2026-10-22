package app.gloss.learn

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** A word the reader starred, with where they met it. */
data class SavedWord(
    val wordId: Long,
    val text: String,
    val reading: String,
    val meaning: String,
    val issueId: String,
    val issueTitle: String,
    val page: Int,
    /** The passage it came from, in Japanese, for context on review. */
    val context: String,
    val savedAt: Long,
)

/** The reader's saved words, newest first, kept as JSON in private storage. */
class WordBook(private val file: File) {
    private val _words = MutableStateFlow(load())
    val words: StateFlow<List<SavedWord>> = _words.asStateFlow()

    fun has(wordId: Long): Boolean = _words.value.any { it.wordId == wordId }

    fun toggle(word: SavedWord) {
        val now = _words.value
        _words.value = if (now.any { it.wordId == word.wordId }) now.filterNot { it.wordId == word.wordId } else listOf(word) + now
        save()
    }

    fun remove(wordId: Long) { _words.value = _words.value.filterNot { it.wordId == wordId }; save() }

    /** Tab-separated front, back and tags: Anki's "Import File" takes it as is. */
    fun ankiExport(): String = buildString {
        append("#separator:tab\n#html:false\n#tags column:3\n")
        for (w in _words.value) {
            val front = w.text.clean()
            val back = listOf(w.reading, w.meaning, w.context.take(140)).filter { it.isNotBlank() }.joinToString(" — ") { it.clean() }
            append(front).append('\t').append(back).append('\t').append("gloss").append('\n')
        }
    }

    private fun String.clean() = replace('\t', ' ').replace('\n', ' ')

    private fun load(): List<SavedWord> = runCatching {
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SavedWord(
                o.getLong("id"), o.getString("text"), o.optString("reading"), o.optString("meaning"),
                o.optString("issue"), o.optString("issueTitle"), o.optInt("page"), o.optString("context"), o.optLong("at"),
            )
        }
    }.getOrDefault(emptyList())

    private fun save() {
        val arr = JSONArray()
        _words.value.forEach { w ->
            arr.put(JSONObject().put("id", w.wordId).put("text", w.text).put("reading", w.reading).put("meaning", w.meaning)
                .put("issue", w.issueId).put("issueTitle", w.issueTitle).put("page", w.page).put("context", w.context).put("at", w.savedAt))
        }
        runCatching {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(file)
        }
    }
}

/**
 * Says Japanese aloud with the phone's own text-to-speech. Started on first use; [speak]
 * reports false when the phone has no Japanese voice, so the UI can say how to get one.
 */
class Speaker(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    /** Null until the engine has answered; then whether a Japanese voice is available. */
    private val _hasVoice = MutableStateFlow<Boolean?>(null)
    val hasVoice: StateFlow<Boolean?> = _hasVoice.asStateFlow()

    fun speak(text: String) {
        val t = tts
        if (t == null) {
            pending = text
            tts = TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                val ok = ready && tts?.setLanguage(Locale.JAPAN)?.let { it >= TextToSpeech.LANG_AVAILABLE } == true
                _hasVoice.value = ok
                if (ok) pending?.let { tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "gloss") }
                pending = null
            }
            return
        }
        if (ready && _hasVoice.value == true) t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gloss")
    }
}
