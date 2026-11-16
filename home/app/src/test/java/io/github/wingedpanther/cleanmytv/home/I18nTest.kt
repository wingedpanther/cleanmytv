package io.github.wingedpanther.cleanmytv.home

import io.github.wingedpanther.cleanmytv.home.customization.CustomizationCategory
import io.github.wingedpanther.cleanmytv.home.customization.SETTINGS_INDEX
import io.github.wingedpanther.cleanmytv.home.customization.components.Find
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class I18nTest {
    private val ru: Map<String, String> = I18n.parse(File("src/main/resources/i18n/ru.json").readText())

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

    private fun russian() { I18n.language = { "ru" } }

    @After fun back() {
        I18n.language = { java.util.Locale.getDefault().language }
        I18n.source = { lang -> I18n::class.java.getResourceAsStream("/i18n/$lang.json")?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } }
        I18n.reset()
    }

    @Test fun `every text in the code has its Russian`() {
        val missing = (literals.keys + tables).filter { it !in ru }
        assertTrue("not in ru.json: $missing", missing.isEmpty())
    }

    @Test fun `ru json has nothing the code no longer uses`() {
        val unused = ru.keys - literals.keys - tables
        assertTrue("unused in ru.json: $unused", unused.isEmpty())
    }

    @Test fun `a key is plain text, never a Kotlin template`() {
        val templated = literals.filter { (k, _) -> Regex("""\$[{\w]""").containsMatchIn(k) }
        assertTrue("tr() keys with \$: $templated", templated.isEmpty())
    }

    @Test fun `the Russian fills in the same placeholders as the English`() {
        val wrong = ru.filter { (en, tr) -> placeholders(en) != placeholders(tr) }
        assertTrue("placeholders differ: $wrong", wrong.isEmpty())
        val blank = ru.filter { (en, tr) -> en.isNotBlank() && tr.isBlank() }
        assertTrue("empty translations: $blank", blank.isEmpty())
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

    @Test fun `a search in Russian lands on the same control as in English`() {
        val labels = (literals.keys + tables).filter { k -> SETTINGS_INDEX.none { it.words == k } }
        val lost = SETTINGS_INDEX.flatMap { f ->
            labels.filter { Find.matches(f.name, it) && !Find.matches(ru[f.name], ru[it] ?: it) }.map { f.name to it }
        }
        assertTrue("search misses in Russian: $lost", lost.isEmpty())
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
