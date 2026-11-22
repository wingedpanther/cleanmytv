package io.github.wingedpanther.cleanmytv.home

import org.json.JSONObject
import java.util.Locale

/**
 * Home's text in the TV's language. The English stays in the code and is the key; i18n/<language>.json maps
 * it to that language (ru.json, de.json). Another language is only its file: nothing here names one. A
 * missing entry shows the English, and so does a language with no file. Each map is read once, the first
 * time it's needed, from the APK's own resources, so the lock screen, the button service and plain JVM tests
 * need no Context.
 */
object I18n {
    /**
     * The language to show, the TV's own unless a test picks one. From the language tag, which says "he" and
     * "id" where Locale.language still says "iw" and "in", so the file is named the way people expect.
     */
    @Volatile var language: () -> String = { Locale.getDefault().toLanguageTag().substringBefore('-').lowercase() }

    /** Where a language's map comes from, as JSON text, or null when there is none. Tests swap it. */
    @Volatile var source: (String) -> String? = { lang ->
        I18n::class.java.getResourceAsStream("/i18n/$lang.json")?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
    }

    private val tables = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()

    /** The English to [lang] map, read on first use. An unreadable file counts as empty, so Home stays in English. */
    fun table(lang: String): Map<String, String> = tables.getOrPut(lang) {
        runCatching { source(lang)?.let(::parse) }.getOrNull() ?: emptyMap()
    }

    /** Forgets the maps and plural rules, for tests that swap [source] or [rules]. */
    fun reset() { tables.clear(); pluralCache.clear() }

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

    /** Plural categories in the order a translation lists its forms, as CLDR names them. */
    private val ORDER = listOf("zero", "one", "two", "few", "many", "other")

    /** One for 1, other for the rest, for when a language's rules can't be read. */
    private val ONE_OTHER: (Long) -> String = { if (it == 1L) "one" else "other" }

    /** A language's plural rule: the CLDR category for a whole number. Android's ICU on the TV; tests swap in ICU4J. */
    @Volatile var rules: (String) -> (Long) -> String = { lang ->
        android.icu.text.PluralRules.forLocale(Locale(lang)).let { r -> { n: Long -> r.select(n.toDouble()) } }
    }

    /**
     * The forms a language's counts take, as the categories whole numbers below 200 fall in: one and other in
     * English and German, one, few and many in Russian. A category only round millions reach, like Spanish
     * "many", isn't one; those numbers take the last form.
     */
    class Plurals(val forms: List<String>, private val pick: (Long) -> String) {
        fun index(n: Long): Int = forms.indexOf(pick(n)).let { if (it < 0) forms.size - 1 else it }
    }

    private val pluralCache = java.util.concurrent.ConcurrentHashMap<String, Plurals>()

    fun plurals(lang: String): Plurals = pluralCache.getOrPut(lang) {
        val pick = runCatching { rules(lang).also { it(1L) } }.getOrNull() ?: ONE_OTHER
        Plurals((0L until 200L).map(pick).distinct().sortedBy { ORDER.indexOf(it) }, pick)
    }

    /** Which form goes with [n] things, counting from 0 in the order the translation lists them. */
    fun pluralIndex(n: Long, lang: String = language()): Int = plurals(lang).index(kotlin.math.abs(n))
}

/** [english] in the TV's language: tr("After {n} min", "n" to 5). */
fun tr(english: String, vararg args: Pair<String, Any>): String = I18n.text(english, args)
