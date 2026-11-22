# Translating CleanMyTV

## Adding a language

A language is two files, and nothing else changes:

| File | What it translates |
| --- | --- |
| `home/app/src/main/resources/i18n/<code>.json` | CleanMyTV Home, the launcher on the TV |
| `src/renderer/src/locales/<code>.json` | The desktop app |

`<code>` is the language's two-letter code, lowercase: `fr`, `pt`, `pl`, `he`. Either file can come on its own.

Home shows its file when the TV is set to that language. The desktop app lists the language in its menu and picks it when the computer runs in it. Both find the files by themselves when they're built.

## The format

Each file maps the English text, exactly as the code has it, to the translation:

```json
{
  "_name": "Français",
  "Settings": "Paramètres",
  "After {n} min": "Après {n} min"
}
```

`_name` goes in the desktop file only. It's the language's name as its speakers write it, for the language menu.

A line left out shows the English. The tests still want every line, so leave a line out only while you work.

## Placeholders

A word in braces, like `{n}`, `{name}` or `{app}`, is filled in by the app. Keep it exactly as it is. You can move it anywhere in the sentence.

## Plurals

A count picks its word with `{n|...}`, the forms split by `|`. English has two forms, one and other:

```json
"{n} apps": "{n} {n|app|apps}"
```

Your language uses its own number of forms, in the order of its plural rules: zero, one, two, few, many, other, leaving out the ones it doesn't have. Russian has three, one (1, 21, 101), few (2 to 4, 22 to 24) and many (5 to 20, 25 and so on):

```json
"{n} apps": "{n} {n|приложение|приложения|приложений}"
```

German and Spanish have two, like English. Czech and Polish have three. Japanese and Chinese have one: `{n|アプリ}`.

The count of forms comes from the [CLDR plural rules](https://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html), but only the forms whole numbers up to 199 use. So Russian's "other", which only fractions take, and the "many" that Spanish, French and Italian keep for round millions, are left out.

## Checking

The tests read every file in both folders and fail on a missing line, a line the app no longer uses, a placeholder that differs from the English, or a plural with the wrong number of forms:

```sh
cd home && ./gradlew testDebugUnitTest
npx vitest run tests/i18n.test.ts
```

When the code gets a new English text, every language file needs it too.

## The spreadsheet

For people who'd rather not edit JSON, `scripts/translation.py` makes a spreadsheet with every English text, and reads a filled-in one back into both files:

```sh
python3 scripts/translation.py sheet out.xlsx
python3 scripts/translation.py import fr filled.xlsx "Français"
```
