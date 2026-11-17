package io.github.wingedpanther.cleanmytv.home

import org.json.JSONObject
import java.util.Locale

/**
 * Home's text in the TV's language. The English stays in the code and is the key; i18n/<language>.json maps
 * it to that language (ru.json, de.json). A missing entry shows the English, and so does a language with no
 * file. Each map is read once, the first time it's needed, from the APK's own resources, so the lock screen,
 * the button service and plain JVM tests need no Context.
 */
object I18n {
    /** The language to show, the TV's own unless a test picks one. */
    @Volatile var language: () -> String = { Locale.getDefault().language }

    /** Where a language's map comes from, as JSON text, or null when there is none. Tests swap it. */
    @Volatile var source: (String) -> String? = { lang ->
        I18n::class.java.getResourceAsStream("/i18n/$lang.json")?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
    }

    private val tables = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()

    /** The English to [lang] map, read on first use. An unreadable file counts as empty, so Home stays in English. */
    fun table(lang: String): Map<String, String> = tables.getOrPut(lang) {
        runCatching { source(lang)?.let(::parse) }.getOrNull() ?: emptyMap()
    }

    /** Forgets the maps, for tests that swap [source]. */
    fun reset() = tables.clear()

    fun parse(json: String): Map<String, String> {
        val o = JSONObject(json)
        return o.keys().asSequence().associateWith { o.getString(it) }
    }

    /** [english] in the TV's language, with each {name} filled in from [args]. */
    fun text(english: String, args: Array<out Pair<String, Any>>): String {
        val lang = language()
        val s = if (lang == "en") english else table(lang)[english] ?: english
        return if (args.isEmpty()) s else fill(s, args, lang)
    }

    // Every brace escaped: Android's regex engine rejects a bare "}", which the JVM in unit tests accepts.
    private val PLACEHOLDER = Regex("\\{(\\w+)(?:\\|([^\\{\\}]*))?\\}")

    /** {name} takes the value; {n|one|other} or {n|one|few|many} picks the word that goes with the number n. */
    fun fill(s: String, args: Array<out Pair<String, Any>>, lang: String = language()): String {
        val values = args.toMap()
        return PLACEHOLDER.replace(s) { m ->
            val v = values[m.groupValues[1]] ?: return@replace m.value
            val forms = m.groups[2]?.value?.split('|')
            if (forms == null || forms.size < 2) v.toString()
            else forms[pluralIndex((v as? Number)?.toLong() ?: v.toString().toLongOrNull() ?: 0L, lang).coerceAtMost(forms.size - 1)]
        }
    }

    /**
     * Which form goes with [n] things. Russian, Ukrainian and Belarusian have three: 1, 21, 101 take the
     * first; 2-4, 22-24 the second; the rest the third. French counts 0 and 1 as one. Most others, English
     * and German among them, say one for 1 and the second form for the rest.
     */
    fun pluralIndex(n: Long, lang: String = language()): Int {
        val a = kotlin.math.abs(n)
        return when (lang) {
            "ru", "uk", "be" -> {
                val ten = a % 10
                val hundred = a % 100
                when {
                    ten == 1L && hundred != 11L -> 0
                    ten in 2..4 && hundred !in 12..14 -> 1
                    else -> 2
                }
            }
            "fr" -> if (a <= 1L) 0 else 1
            else -> if (a == 1L) 0 else 1
        }
    }
}

/** [english] in the TV's language: tr("After {n} min", "n" to 5). */
fun tr(english: String, vararg args: Pair<String, Any>): String = I18n.text(english, args)
