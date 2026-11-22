"""The translation spreadsheet: makes one for volunteers, and reads a filled-in one back into the language files.

    python3 scripts/translation.py sheet out.xlsx                     every English text, with an empty column to fill in
    python3 scripts/translation.py import fr filled.xlsx "Français"  writes locales/fr.json and Home's i18n/fr.json

The sheet has a tab for the TV Home screen and one for the desktop app, English in column A and the
translation in column B. A text both apps use is listed once. Needs openpyxl (pip install openpyxl).
The name is how the language's speakers write it, for the desktop app's language menu; a language that
already has a file keeps its name when it's left out. Both apps find the new files by themselves.
See docs/TRANSLATING.md.
"""
import json
import re
import sys
from pathlib import Path

from openpyxl import Workbook, load_workbook
from openpyxl.styles import Alignment, Font, PatternFill

ROOT = Path(__file__).resolve().parent.parent
# Where a desktop language file keeps the language's own name (NAME_KEY in src/shared/i18n.ts).
NAME = "_name"
DESKTOP = ROOT / "src/renderer/src/locales"
HOME = ROOT / "home/app/src/main/resources/i18n"
TABS = [("TV Home screen", HOME), ("Desktop app", DESKTOP)]
HOW = [
    "CleanMyTV translation",
    "",
    'Pick a tab: "TV Home screen" (start here, everyone sees it) or "Desktop app".',
    "Write your translation in column B, next to the English in column A.",
    "Keep anything in {curly brackets} exactly as it is, like {n} or {name}.",
    "Plurals look like {n|file|files}. Put your language's forms in the same spot, e.g. {n|Datei|Dateien}.",
    "Leave a line empty if you're not sure. English shows for that line.",
    "Send the file back on Discord or GitHub.",
]


def english(folder: Path) -> list[str]:
    """The English texts an app asks for, from its Russian file, which the tests keep complete."""
    return [k for k in json.loads((folder / "ru.json").read_text("utf-8")) if k != NAME]


def sheet(out: str) -> None:
    wb = Workbook()
    how = wb.active
    how.title = "How to"
    for i, line in enumerate(HOW, 1):
        how.cell(i, 1, line).font = Font(bold=i == 1, size=14 if i == 1 else 11)
    how.column_dimensions["A"].width = 110
    seen: set[str] = set()
    for title, folder in TABS:
        ws = wb.create_sheet(title)
        ws.append(["English", "Your translation"])
        for c in ws[1]:
            c.font = Font(bold=True)
            c.fill = PatternFill("solid", fgColor="DDDDDD")
        for text in english(folder):
            if text not in seen:
                seen.add(text)
                ws.append([text, ""])
        ws.freeze_panes = "A2"
        for col in "AB":
            ws.column_dimensions[col].width = 70
        for row in ws.iter_rows(min_row=2):
            for c in row:
                c.alignment = Alignment(wrap_text=True, vertical="top")
    wb.save(out)
    print(f"{out}: {len(seen)} texts")


def tokens(s: str) -> set[str]:
    return set(re.findall(r"\{(\w+)(?:\|[^{}]*)?\}", s))


def read(path: str) -> dict[str, str]:
    """English to translation from every tab but the first. Empty lines are left out, so they stay English."""
    wb = load_workbook(path, read_only=True)
    out: dict[str, str] = {}
    for ws in wb.worksheets[1:]:
        for row in list(ws.iter_rows(values_only=True))[1:]:
            en, tr = (row + (None, None))[:2]
            if en and tr and str(tr).strip():
                out[str(en)] = str(tr)
    return out


def import_(lang: str, path: str, name: str | None) -> None:
    if not re.fullmatch(r"[a-z]{2,3}", lang) or lang in ("en", "ru"):
        sys.exit(f"not a language to import: {lang}")
    old = DESKTOP / f"{lang}.json"
    name = (name or "").strip() or (json.loads(old.read_text("utf-8")).get(NAME) if old.exists() else None)
    if not name:
        sys.exit(f'{lang} needs its name, as its speakers write it: import {lang} {path} "Name"')
    got = read(path)
    for title, folder in TABS:
        texts = english(folder)
        out, missing, wrong = ({NAME: name} if folder == DESKTOP else {}), [], []
        for en in texts:
            tr = got.get(en)
            if tr is None:
                missing.append(en)
            elif tokens(tr) != tokens(en):
                wrong.append(en)
            else:
                out[en] = tr
        (folder / f"{lang}.json").write_text(json.dumps(out, ensure_ascii=False, indent=2) + "\n", "utf-8")
        print(f"{title}: {len(out) - (folder == DESKTOP)} of {len(texts)} translated, {len(missing)} missing, {len(wrong)} with changed {{placeholders}} left out")
        for en in wrong:
            print(f"  placeholders differ: {en!r}")


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "sheet":
        sheet(sys.argv[2])
    elif len(sys.argv) in (4, 5) and sys.argv[1] == "import":
        import_(sys.argv[2], sys.argv[3], sys.argv[4] if len(sys.argv) == 5 else None)
    else:
        sys.exit(__doc__)
