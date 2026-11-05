package com.shinjikai.dictionary.integration

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import com.ichi2.anki.api.AddContentApi
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private const val ANKIDROID_PACKAGE = "com.ichi2.anki"
const val ANKIDROID_PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
private const val SHINJIKAI_DECK_NAME = "Shinjikai"
private const val SHINJIKAI_MODEL_NAME = "com.shinjikai.dictionary.bento.v7"
private val SHINJIKAI_MODEL_FIELDS = arrayOf("Expression", "Reading", "Meaning", "Example", "Audio")
private val SHINJIKAI_CARD_NAMES = arrayOf("Meaning")

// Runs inside AnkiDroid's card WebView. Plain ES5 so it works on old WebViews; if it
// fails or scripts are disabled, the fields still render as plain pre-wrapped text.
private val SHINJIKAI_CARD_SCRIPT = """
    <script>
    (function () {
      function esc(s) {
        return String(s).replace(/[&<>"]/g, function (c) {
          return { "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;" }[c];
        });
      }
      function lines(el) {
        var holder = document.createElement("div");
        holder.innerHTML = el.innerHTML
          .replace(/<br\s*\/?>/gi, "\n")
          .replace(/<\/(div|p)>/gi, "\n");
        return holder.textContent.split("\n")
          .map(function (l) { return l.trim(); })
          .filter(function (l) { return l.length > 0 && l !== "-"; });
      }
      var exprEl = document.querySelector(".sj-expr");
      var word = exprEl ? exprEl.textContent.trim() : "";
      var kanji = word.match(/[㐀-鿿]/);
      var markChar = kanji ? kanji[0] : word.charAt(0);
      var marks = document.querySelectorAll(".sj-mark");
      for (var i = 0; i < marks.length; i++) marks[i].textContent = markChar;

      var meaning = document.querySelector(".sj-meaning");
      if (meaning && lines(meaning).length === 0) {
        var meaningCard = meaning.parentNode;
        if (meaningCard) meaningCard.style.display = "none";
        meaning = null;
      }
      if (meaning) {
        var raw = lines(meaning);
        var grouped = raw.some(function (l) { return l.indexOf("- ") === 0; });
        var entries = [];
        raw.forEach(function (l) {
          if (!grouped || l.indexOf("- ") === 0 || entries.length === 0) {
            entries.push({ text: grouped ? l.replace(/^-\s*/, "") : l, notes: [] });
          } else {
            entries[entries.length - 1].notes.push(l);
          }
        });
        if (entries.length > 0) {
          meaning.innerHTML = entries.map(function (e, idx) {
            var notes = e.notes.map(function (n) {
              return "<div class=\"sj-note\">" + esc(n) + "</div>";
            }).join("");
            return "<div class=\"sj-row\"><span class=\"sj-num\">" + (idx + 1) +
              "</span><div class=\"sj-row-text\"><div>" + esc(e.text) + "</div>" + notes + "</div></div>";
          }).join("");
          meaning.className += " sj-ready";
        }
      }

      var example = document.querySelector(".sj-example");
      if (example) {
        var ex = lines(example);
        if (ex.length > 0) {
          var jp = esc(ex[0]);
          var target = word;
          while (target.length > 1 && ex[0].indexOf(target) < 0 && /[぀-ゟ]$/.test(target)) {
            target = target.slice(0, -1);
          }
          if (target && ex[0].indexOf(target) >= 0) {
            jp = jp.split(esc(target)).join("<mark>" + esc(target) + "</mark>");
          }
          var ar = ex.slice(1).map(esc).join("<br>");
          example.innerHTML = "<div class=\"sj-ex-jp\" lang=\"ja\" dir=\"ltr\">" + jp + "</div>" +
            (ar ? "<div class=\"sj-ex-ar\">" + ar + "</div>" : "");
          example.className += " sj-ready";
        }
      }
    })();
    </script>
""".trimIndent()

private val SHINJIKAI_QUESTION_TEMPLATES = arrayOf(
    """
    <div class="sj" dir="rtl">
      <div class="sj-card sj-front">
        <div class="sj-mark" aria-hidden="true"></div>
        <div class="sj-top">
          <span class="sj-brand"><span class="sj-badge" lang="ja">深</span>شِنجِكاي</span>
          <span class="sj-muted">ما معنى هذه الكلمة؟</span>
        </div>
        <div class="sj-center">
          <div class="sj-expr" lang="ja" dir="ltr">{{Expression}}</div>
          {{#Audio}}<div class="sj-audio">{{Audio}}</div>{{/Audio}}
        </div>
        <div class="sj-muted sj-hint">تذكّر المعنى ثم اكشف الإجابة</div>
      </div>
    </div>
    """.trimIndent() + "\n" + SHINJIKAI_CARD_SCRIPT
)
private val SHINJIKAI_ANSWER_TEMPLATES = arrayOf(
    """
    <div class="sj sj-back" dir="rtl">
      <div class="sj-card sj-word">
        <div class="sj-mark" aria-hidden="true"></div>
        {{#Reading}}<div class="sj-reading" lang="ja" dir="ltr">{{Reading}}</div>{{/Reading}}
        <div class="sj-expr" lang="ja" dir="ltr">{{Expression}}</div>
        {{#Audio}}<div class="sj-audio">{{Audio}}</div>{{/Audio}}
      </div>
      <div class="sj-card sj-section">
        <div class="sj-caption">المعنى</div>
        <div class="sj-meaning">{{Meaning}}</div>
      </div>
      {{#Example}}
      <div class="sj-card sj-section">
        <div class="sj-caption">مثال</div>
        <div class="sj-example">{{Example}}</div>
      </div>
      {{/Example}}
      <div class="sj-muted sj-signature"><span lang="ja">深辞海</span> · قاموس شِنجِكاي</div>
    </div>
    """.trimIndent() + "\n" + SHINJIKAI_CARD_SCRIPT
)
private const val SHINJIKAI_CARD_CSS = """
    .card {
      --sj-bg: #FAF8F7;
      --sj-surface: #FFFFFF;
      --sj-high: #F6F2F1;
      --sj-border: #E1D8D6;
      --sj-text: #211B1A;
      --sj-sub: #5D504E;
      --sj-muted: #7A6866;
      --sj-primary: #9B3C36;
      --sj-on-primary: #FFFFFF;
      --sj-pc: #FFDAD5;
      --sj-on-pc: #3A0806;
      --sj-mark: rgba(155, 60, 54, 0.07);
    }
    .nightMode, .night_mode, .nightMode .card, .night_mode .card, .card.nightMode, .card.night_mode {
      --sj-bg: #0E0D0D;
      --sj-surface: #171515;
      --sj-high: #1E1B1B;
      --sj-border: #2A2423;
      --sj-text: #F0EDEC;
      --sj-sub: #CFC5C3;
      --sj-muted: #9A8A88;
      --sj-primary: #FF9E98;
      --sj-on-primary: #470B08;
      --sj-pc: #5D2724;
      --sj-on-pc: #FFDAD7;
      --sj-mark: rgba(93, 39, 36, 0.24);
    }
    html, body, .card {
      margin: 0;
      background: var(--sj-bg) !important;
      color: var(--sj-text) !important;
    }
    .card {
      padding: 16px 14px;
      min-height: 100vh;
      box-sizing: border-box;
      font-family: "IBM Plex Sans Arabic", "Noto Sans Arabic", system-ui, sans-serif;
      font-size: 16px;
      text-align: right;
    }
    [lang="ja"] { font-family: "Noto Sans CJK JP", "Noto Sans JP", "Hiragino Sans", sans-serif; }
    .sj {
      max-width: 560px;
      margin: 0 auto;
      display: flex;
      flex-direction: column;
      gap: 10px;
    }
    .sj-card {
      position: relative;
      overflow: hidden;
      background: var(--sj-surface);
      border: 1px solid var(--sj-border);
      border-radius: 28px;
      box-sizing: border-box;
    }
    .sj-mark {
      position: absolute;
      top: -8px;
      left: 8px;
      font-size: 140px;
      font-weight: 700;
      line-height: 1;
      color: var(--sj-mark);
      pointer-events: none;
      font-family: "Noto Sans CJK JP", "Noto Sans JP", sans-serif;
    }
    .sj-front {
      min-height: calc(100vh - 32px);
      padding: 20px;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
    }
    .sj-top, .sj-center, .sj-hint, .sj-reading, .sj-word .sj-expr, .sj-word .sj-audio { position: relative; }
    .sj-top {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
    }
    .sj-brand {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      height: 30px;
      padding: 0 10px 0 4px;
      border-radius: 999px;
      background: var(--sj-high);
      border: 1px solid var(--sj-border);
      font-size: 12px;
      font-weight: 600;
      color: var(--sj-sub);
    }
    .sj-badge {
      width: 22px;
      height: 22px;
      border-radius: 8px;
      background: var(--sj-primary);
      color: var(--sj-on-primary);
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-size: 12px;
      font-weight: 700;
    }
    .sj-muted { font-size: 12px; color: var(--sj-muted); }
    .sj-hint, .sj-signature { text-align: center; }
    .sj-center {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 22px;
      padding: 24px 0;
    }
    .sj-expr {
      font-size: 56px;
      font-weight: 700;
      line-height: 1.2;
      text-align: center;
      word-break: break-word;
    }
    .sj-word {
      padding: 18px 20px 20px;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 4px;
    }
    .sj-word .sj-mark { font-size: 104px; top: -10px; }
    .sj-word .sj-expr { font-size: 42px; }
    .sj-reading {
      font-size: 16px;
      letter-spacing: 0.08em;
      color: var(--sj-primary);
    }
    .sj-audio { margin-top: 10px; }
    .replay-button, .replaybutton {
      display: inline-flex !important;
      align-items: center;
      justify-content: center;
      gap: 8px;
      height: 44px;
      padding: 0 18px;
      border-radius: 999px;
      background: var(--sj-primary) !important;
      color: var(--sj-on-primary) !important;
      text-decoration: none;
      font-size: 14px;
      font-weight: 700;
      vertical-align: middle;
    }
    .replay-button svg, .replaybutton svg {
      width: 18px !important;
      height: 18px !important;
    }
    .replay-button svg circle, .replaybutton svg circle { fill: none !important; stroke: none !important; }
    .replay-button svg path, .replaybutton svg path { fill: currentColor !important; stroke: none !important; }
    .replay-button::after, .replaybutton::after { content: "نطق"; }
    .sj-section {
      padding: 14px 16px 16px;
      display: flex;
      flex-direction: column;
      gap: 10px;
    }
    .sj-caption { font-size: 12px; font-weight: 600; color: var(--sj-muted); }
    .sj-meaning, .sj-example {
      white-space: pre-wrap;
      font-size: 16px;
      line-height: 1.7;
    }
    .sj-meaning.sj-ready, .sj-example.sj-ready {
      white-space: normal;
      display: flex;
      flex-direction: column;
    }
    .sj-meaning.sj-ready { gap: 10px; }
    .sj-row { display: flex; gap: 10px; align-items: flex-start; }
    .sj-row-text { flex: 1; min-width: 0; }
    .sj-num {
      width: 26px;
      height: 26px;
      flex-shrink: 0;
      margin-top: 2px;
      border-radius: 999px;
      background: var(--sj-pc);
      color: var(--sj-on-pc);
      font-size: 13px;
      font-weight: 700;
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }
    .sj-note { margin-top: 2px; font-size: 13px; color: var(--sj-muted); }
    .sj-example.sj-ready {
      gap: 6px;
      padding: 12px 14px;
      border-radius: 18px;
      background: var(--sj-high);
    }
    .sj-ex-jp { font-size: 17px; text-align: left; }
    .sj-ex-jp mark {
      background: none;
      color: var(--sj-primary);
      font-weight: 700;
    }
    .sj-ex-ar { font-size: 14px; color: var(--sj-sub); }
    .sj-signature { padding-top: 2px; }
"""

data class AnkiNoteContent(
    val expression: String,
    val reading: String,
    val meaning: String,
    val example: String,
    val speechText: String
) {
    fun fields(audio: String = ""): Array<String> = arrayOf(expression, reading, meaning, example, audio)

    val shareSubject: String
        get() = expression

    val shareBody: String
        get() = buildString {
            append(expression)
            if (reading.isNotBlank()) {
                append("\n")
                append(reading)
            }
            append("\n\n")
            append(meaning)
            if (example.isNotBlank()) {
                append("\n\n")
                append(example)
            }
        }.trim()
}

sealed interface AnkiAddResult {
    data class Added(val hasAudio: Boolean) : AnkiAddResult
    data object PermissionRequired : AnkiAddResult
    data object OpenedShareFallback : AnkiAddResult
    data object AnkiNotInstalled : AnkiAddResult
    data object Failed : AnkiAddResult
}

object AnkiExporter {
    fun canRequestDirectAdd(context: Context): Boolean =
        AddContentApi.getAnkiDroidPackageName(context) != null

    fun hasDatabasePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, ANKIDROID_PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun loadDeckNames(context: Context): List<String> {
        if (!isAnkiDroidInstalled(context)) return emptyList()
        if (!canRequestDirectAdd(context)) return emptyList()
        if (!hasDatabasePermission(context)) return emptyList()
        return AddContentApi(context).deckList.values
            .filter { it.isNotBlank() }
            .sorted()
    }

    suspend fun addNote(
        context: Context,
        note: AnkiNoteContent,
        deckName: String = SHINJIKAI_DECK_NAME,
        textToSpeech: TextToSpeech? = null,
        canSpeakJapanese: Boolean = false
    ): AnkiAddResult {
        if (!isAnkiDroidInstalled(context)) return AnkiAddResult.AnkiNotInstalled
        if (!canRequestDirectAdd(context)) return shareToAnki(context, note)
        if (!hasDatabasePermission(context)) return AnkiAddResult.PermissionRequired

        val audioField = if (canSpeakJapanese && textToSpeech != null && note.speechText.isNotBlank()) {
            synthesizeAudioField(
                context = context,
                note = note,
                textToSpeech = textToSpeech
            )
        } else {
            ""
        }

        return runCatching {
            val api = AddContentApi(context)
            val normalizedDeckName = deckName.trim().ifBlank { SHINJIKAI_DECK_NAME }
            val deckId = api.deckList.entries.firstOrNull { it.value == normalizedDeckName }?.key
                ?: api.addNewDeck(normalizedDeckName)
            val modelId = api.modelList.entries.firstOrNull { it.value == SHINJIKAI_MODEL_NAME }?.key
                ?: api.addNewCustomModel(
                    SHINJIKAI_MODEL_NAME,
                    SHINJIKAI_MODEL_FIELDS,
                    SHINJIKAI_CARD_NAMES,
                    SHINJIKAI_QUESTION_TEMPLATES,
                    SHINJIKAI_ANSWER_TEMPLATES,
                    SHINJIKAI_CARD_CSS,
                    null,
                    null
                )

            if (deckId <= 0L || modelId <= 0L) {
                shareToAnki(context, note)
            } else {
                val noteId = api.addNote(modelId, deckId, note.fields(audioField), null)
                if (noteId > 0L) AnkiAddResult.Added(hasAudio = audioField.isNotBlank()) else shareToAnki(context, note)
            }
        }.getOrElse {
            if (it is SecurityException) {
                AnkiAddResult.PermissionRequired
            } else {
                shareToAnki(context, note)
            }
        }
    }

    private suspend fun synthesizeAudioField(
        context: Context,
        note: AnkiNoteContent,
        textToSpeech: TextToSpeech
    ): String = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "anki-audio-${UUID.randomUUID()}.wav")
        val importedName = try {
            val synthResult = synthesizeToFile(
                textToSpeech = textToSpeech,
                text = note.speechText,
                destination = tempFile,
                utteranceId = "anki-audio-${UUID.randomUUID()}"
            )
            if (!synthResult || !tempFile.exists() || tempFile.length() == 0L) {
                ""
            } else {
                val api = AddContentApi(context)
                api.addMediaFromUri(
                    Uri.fromFile(tempFile),
                    tempFile.name,
                    "audio/wav"
                ).orEmpty()
            }
        } finally {
            tempFile.delete()
        }

        if (importedName.isBlank()) "" else "[sound:$importedName]"
    }

    private suspend fun synthesizeToFile(
        textToSpeech: TextToSpeech,
        text: String,
        destination: File,
        utteranceId: String
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val listener = object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(completedUtteranceId: String?) {
                if (completedUtteranceId == utteranceId && continuation.isActive) {
                    continuation.resume(true)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(errorUtteranceId: String?) {
                if (errorUtteranceId == utteranceId && continuation.isActive) {
                    continuation.resume(false)
                }
            }

            override fun onError(errorUtteranceId: String?, errorCode: Int) {
                if (errorUtteranceId == utteranceId && continuation.isActive) {
                    continuation.resume(false)
                }
            }
        }

        textToSpeech.setOnUtteranceProgressListener(listener)
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        val result = textToSpeech.synthesizeToFile(text, params, destination, utteranceId)
        if (result != TextToSpeech.SUCCESS && continuation.isActive) {
            continuation.resume(false)
        }
    }

    fun shareToAnki(context: Context, note: AnkiNoteContent): AnkiAddResult {
        if (!isAnkiDroidInstalled(context)) return AnkiAddResult.AnkiNotInstalled

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            `package` = ANKIDROID_PACKAGE
            putExtra(Intent.EXTRA_SUBJECT, note.shareSubject)
            putExtra(Intent.EXTRA_TEXT, note.shareBody)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(shareIntent)
            AnkiAddResult.OpenedShareFallback
        } catch (_: ActivityNotFoundException) {
            AnkiAddResult.AnkiNotInstalled
        } catch (_: SecurityException) {
            AnkiAddResult.Failed
        }
    }

    private fun isAnkiDroidInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(ANKIDROID_PACKAGE, 0)
        true
    }.getOrDefault(false)
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
