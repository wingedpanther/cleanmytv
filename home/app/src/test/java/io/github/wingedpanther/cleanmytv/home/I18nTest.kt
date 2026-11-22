package io.github.wingedpanther.cleanmytv.home

import io.github.wingedpanther.cleanmytv.home.customization.CustomizationCategory
import io.github.wingedpanther.cleanmytv.home.customization.SETTINGS_INDEX
import io.github.wingedpanther.cleanmytv.home.customization.components.Find
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class I18nTest {
    /** Every language file Home ships, by language: whatever is in i18n/, so a new file is checked as it lands. */
    private val languages: Map<String, Map<String, String>> = File("src/main/resources/i18n").listFiles()!!
        .filter { it.extension == "json" }.associate { it.nameWithoutExtension to I18n.parse(it.readText()) }
    private val ru: Map<String, String> = languages.getValue("ru")

    /** Every literal handed to tr() in Home's code, as Kotlin reads it. */
    private val literals: Map<String, String> = run {
        val call = Regex("""\btr\(\s*"((?:[^"\\]|\\.)*)"""")
        File("src/main/java").walk().filter { it.extension == "kt" }.flatMap { f ->
            call.findAll(f.readText()).map { unescape(it.groupValues[1]) to f.name }
        }.toMap()
    }

    /** The search index and the section names are English in the code and go through tr() when shown. */
    private val tables: Set<String> =
        SETTINGS_INDEX.flatMap { listOf(it.name, it.words) }.toSet() + CustomizationCategory.values().map { it.title }

    private fun unescape(s: String): String = Regex("""\\(u[0-9a-fA-F]{4}|.)""").replace(s) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith("u") && e.length == 5 -> e.substring(1).toInt(16).toChar().toString()
            e == "n" -> "\n"
            e == "t" -> "\t"
            else -> e
        }
    }

    private fun placeholders(s: String) = Regex("""\{(\w+)(?:\|[^{}]*)?}""").findAll(s).map { it.groupValues[1] }.toSet()

    /** The word lists of each {n|...} in [s]. */
    private fun plurals(s: String) = Regex("""\{\w+\|([^{}]*)}""").findAll(s).map { it.groupValues[1].split('|') }.toList()

    private fun russian() { I18n.language = { "ru" } }

    /** ICU4J's rules, the ones Android's own ICU follows on the TV. */
    @Before fun icu() {
        I18n.rules = { lang -> com.ibm.icu.text.PluralRules.forLocale(java.util.Locale(lang)).let { r -> { n: Long -> r.select(n.toDouble()) } } }
        I18n.reset()
    }

    @After fun back() {
        I18n.language = { java.util.Locale.getDefault().toLanguageTag().substringBefore('-').lowercase() }
        I18n.source = { lang -> I18n::class.java.getResourceAsStream("/i18n/$lang.json")?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } }
        I18n.reset()
    }

    @Test fun `every text in the code has its translation in each language`() {
        assertTrue("no files in i18n/", languages.size >= 2)
        val names = Regex("^[a-z]{2,3}$")
        assertTrue("files not named by a language code: ${languages.keys}", languages.keys.all { names.matches(it) })
        for ((lang, map) in languages) {
            val missing = (literals.keys + tables).filter { it !in map }
            assertTrue("not in $lang.json: $missing", missing.isEmpty())
        }
    }

    @Test fun `no language file has text the code no longer uses`() {
        for ((lang, map) in languages) {
            val unused = map.keys - literals.keys - tables
            assertTrue("unused in $lang.json: $unused", unused.isEmpty())
        }
    }

    @Test fun `a key is plain text, never a Kotlin template`() {
        val templated = literals.filter { (k, _) -> Regex("""\$[{\w]""").containsMatchIn(k) }
        assertTrue("tr() keys with \$: $templated", templated.isEmpty())
    }

    @Test fun `each translation fills in the same placeholders as the English`() {
        for ((lang, map) in languages) {
            val wrong = map.filter { (en, tr) -> placeholders(en) != placeholders(tr) }
            assertTrue("$lang placeholders differ: $wrong", wrong.isEmpty())
            val blank = map.filter { (en, tr) -> en.isNotBlank() && tr.isBlank() }
            assertTrue("$lang empty translations: $blank", blank.isEmpty())
        }
    }

    @Test fun `each plural in a translation has the language's number of forms`() {
        assertTrue("English keys need {n|one|other}", (literals.keys + tables).flatMap(::plurals).all { it.size == 2 })
        for ((lang, map) in languages) {
            val forms = I18n.plurals(lang).forms.size
            val wrong = map.filter { (_, tr) -> plurals(tr).any { it.size != forms || it.any(String::isBlank) } }
            assertTrue("$lang plurals need $forms forms: $wrong", wrong.isEmpty())
        }
    }

    @Test fun `a language's forms come from its own rules`() {
        assertEquals(listOf("one", "other"), I18n.plurals("en").forms)
        assertEquals(listOf("one", "other"), I18n.plurals("de").forms)
        assertEquals(listOf("one", "few", "many"), I18n.plurals("ru").forms)
        assertEquals(listOf("one", "few", "other"), I18n.plurals("cs").forms)
        assertEquals(listOf("other"), I18n.plurals("ja").forms)
        // Spanish's "many" is for round millions only, which read fine with the other form.
        assertEquals(listOf("one", "other"), I18n.plurals("es").forms)
        assertEquals(1, I18n.pluralIndex(1_000_000, "es"))
        assertEquals(0, I18n.pluralIndex(0, "fr"))
    }

    @Test fun `without readable rules a count takes one and other`() {
        I18n.rules = { throw RuntimeException("Stub!") }
        I18n.reset()
        assertEquals(listOf("one", "other"), I18n.plurals("ru").forms)
        assertEquals("1 app, 5 apps", tr("{a} {a|app|apps}, {b} {b|app|apps}", "a" to 1, "b" to 5))
    }

    @Test fun `German counts take one and other`() {
        I18n.language = { "de" }
        I18n.source = { """{"{n} apps":"{n} {n|App|Apps}"}""" }
        I18n.reset()
        assertEquals(listOf("0 Apps", "1 App", "2 Apps", "21 Apps"), listOf(0, 1, 2, 21).map { tr("{n} apps", "n" to it) })
    }

    @Test fun `a language with no file stays English`() {
        I18n.language = { "xx" }
        I18n.reset()
        assertEquals("Off", tr("Off"))
    }

    @Test fun `the APK carries the same ru json the tests read`() {
        russian()
        assertEquals(ru, I18n.table("ru"))
        assertEquals("Быстрые настройки", tr("Quick settings"))
    }

    @Test fun `English stays English and a missing entry falls back to it`() {
        I18n.source = { """{"Off":"Выкл.","After {n} min":"Через {n} мин"}""" }
        I18n.reset()
        assertEquals("Off", tr("Off"))
        assertEquals("After 5 min", tr("After {n} min", "n" to 5))
        russian()
        assertEquals("Выкл.", tr("Off"))
        assertEquals("Через 5 мин", tr("After {n} min", "n" to 5))
        assertEquals("Never", tr("Never"))
        assertEquals("After 2 h", tr("After {n} h", "n" to 2))
    }

    @Test fun `a missing or broken file leaves Home in English`() {
        russian()
        I18n.source = { null }
        I18n.reset()
        assertEquals("Off", tr("Off"))
        I18n.source = { "not json" }
        I18n.reset()
        assertEquals("Off", tr("Off"))
    }

    @Test fun `the map is read once`() {
        russian()
        var reads = 0
        I18n.source = { reads++; """{"Off":"Выкл."}""" }
        I18n.reset()
        repeat(50) { tr("Off"); tr("On") }
        assertEquals(1, reads)
    }

    @Test fun `Russian counts take one, few and many`() {
        russian()
        I18n.source = { """{"{n} apps":"{n} {n|приложение|приложения|приложений}"}""" }
        I18n.reset()
        val said = listOf(0, 1, 2, 4, 5, 11, 12, 14, 21, 22, 25, 101, 111).associateWith { tr("{n} apps", "n" to it) }
        assertEquals("0 приложений", said[0])
        assertEquals("1 приложение", said[1])
        assertEquals("2 приложения", said[2])
        assertEquals("4 приложения", said[4])
        assertEquals("5 приложений", said[5])
        assertEquals("11 приложений", said[11])
        assertEquals("12 приложений", said[12])
        assertEquals("14 приложений", said[14])
        assertEquals("21 приложение", said[21])
        assertEquals("22 приложения", said[22])
        assertEquals("25 приложений", said[25])
        assertEquals("101 приложение", said[101])
        assertEquals("111 приложений", said[111])
    }

    @Test fun `a search in each language lands on the same control as in English`() {
        val labels = (literals.keys + tables).filter { k -> SETTINGS_INDEX.none { it.words == k } }
        for ((lang, map) in languages) {
            val lost = SETTINGS_INDEX.flatMap { f ->
                labels.filter { Find.matches(f.name, it) && !Find.matches(map[f.name], map[it] ?: it) }.map { f.name to it }
            }
            assertTrue("search misses in $lang: $lost", lost.isEmpty())
        }
    }

    @Test fun `the TV's language comes from its tag, so Hebrew is he and not iw`() {
        val was = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("he-IL"))
            assertEquals("he", I18n.language())
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("pt-BR"))
            assertEquals("pt", I18n.language())
        } finally {
            java.util.Locale.setDefault(was)
        }
        assertFalse(I18n.language().contains('-'))
    }

    @Test fun `Russian words find the setting`() {
        russian()
        assertEquals("Font", io.github.wingedpanther.cleanmytv.home.customization.searchSettings("шрифт").first().name)
        assertEquals("App lock", io.github.wingedpanther.cleanmytv.home.customization.searchSettings("блокировка").first().name)
        assertEquals("Weather", io.github.wingedpanther.cleanmytv.home.customization.searchSettings("погода").first().name)
        // English words still work on a Russian TV.
        assertEquals("Font", io.github.wingedpanther.cleanmytv.home.customization.searchSettings("font").first().name)
    }
}
