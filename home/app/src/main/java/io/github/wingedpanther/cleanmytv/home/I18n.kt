package io.github.wingedpanther.cleanmytv.home

import org.json.JSONObject
import java.util.Locale

/**
 * Home's text in the TV's language. The English stays in the code and is the key; i18n/ru.json maps it to
 * Russian. A missing entry shows the English. The map is read once, the first time Russian is needed, from
 * the APK's own resources, so the lock screen, the button service and plain JVM tests need no Context.
 */
object I18n {
    /** The language to show, the TV's own unless a test picks one. */
    @Volatile var language: () -> String = { Locale.getDefault().language }

    /** Where a language's map comes from, as JSON text, or null when there is none. Tests swap it. */
    @Volatile var source: (String) -> String? = { lang ->
        I18n::class.java.getResourceAsStream("/i18n/$lang.json")?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
    }

    private val tables = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()

    /** The English to Russian map, read on first use. An unreadable file counts as empty, so Home stays in English. */
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
        val s = if (lang == "ru") table(lang)[english] ?: english else english
        return if (args.isEmpty()) s else fill(s, args, lang)
    }

    private val PLACEHOLDER = Regex("\\{(\\w+)(?:\\|([^{}]*))?}")

    /** {name} takes the value; {n|one|few|many} picks the Russian word that goes with the number n. */
    fun fill(s: String, args: Array<out Pair<String, Any>>, lang: String = language()): String {
        val values = args.toMap()
        return PLACEHOLDER.replace(s) { m ->
            val v = values[m.groupValues[1]] ?: return@replace m.value
            val forms = m.groups[2]?.value?.split('|')
            if (forms == null || forms.size != 3) v.toString()
            else plural((v as? Number)?.toLong() ?: v.toString().toLongOrNull() ?: 0L, forms[0], forms[1], forms[2], lang)
        }
    }

    /**
     * The word for [n] things: 1, 21, 101 take [one]; 2-4, 22-24 take [few]; the rest [many]. English
     * only has [one] and [many].
     */
    fun plural(n: Long, one: String, few: String, many: String, lang: String = language()): String {
        val a = kotlin.math.abs(n)
        if (lang != "ru") return if (a == 1L) one else many
        val ten = a % 10
        val hundred = a % 100
        return when {
            ten == 1L && hundred != 11L -> one
            ten in 2..4 && hundred !in 12..14 -> few
            else -> many
        }
    }
}

/** [english] in the TV's language: tr("After {n} min", "n" to 5). */
fun tr(english: String, vararg args: Pair<String, Any>): String = I18n.text(english, args)
