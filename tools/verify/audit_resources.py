"""Second-pass audit: resource references, string parity, and symbol references."""
import re
import sys
from pathlib import Path

# This file lives in <repo>/tools/verify, so the repository is two directories up.
REPO = Path(__file__).resolve().parents[2]
JAVA = REPO / "app/src/main/java/com/hiapps/hinotes"
RES = REPO / "app/src/main/res"

EN = RES / "values/strings.xml"
ZH = RES / "values-zh-rCN/strings.xml"

problems = []


def kotlin_sources():
    for path in sorted(JAVA.rglob("*.kt")):
        yield path, path.read_text(encoding="utf-8")


def xml_keys(path):
    text = path.read_text(encoding="utf-8")
    # name -> value, tolerant of the odd formatting already present in the files
    return {
        m.group(1): m.group(2)
        for m in re.finditer(r'<string name="([^"]+)"[^>]*>(.*?)</string>', text, re.S)
    }


en = xml_keys(EN)
zh = xml_keys(ZH)

# ---------------------------------------------------------------- string references
referenced = {}
for path, text in kotlin_sources():
    for m in re.finditer(r"R\.string\.(\w+)", text):
        referenced.setdefault(m.group(1), []).append(path.name)

missing = sorted(k for k in referenced if k not in en)
if missing:
    problems.append(f"R.string keys with no declaration: {missing}")

# ---------------------------------------------------------------- en / zh parity
only_en = sorted(set(en) - set(zh))
only_zh = sorted(set(zh) - set(en))
if only_en:
    problems.append(f"declared in English but not Chinese: {only_en}")
if only_zh:
    problems.append(f"declared in Chinese but not English: {only_zh}")

# Chinese entries left in English are almost always an oversight. These are deliberate: the app's
# name is a brand, a repository URL is a URL, and the version line keeps the word "released".
ALLOWED_UNTRANSLATED = {"app_name", "about_repository_support", "about_version_support"}
untranslated = [
    k for k, v in zh.items()
    if k not in ALLOWED_UNTRANSLATED and not re.search(r"[\u4e00-\u9fff]", v)
]
if untranslated:
    problems.append(f"Chinese strings with no Chinese text: {untranslated}")

# ---------------------------------------------------------------- unused strings
unused = sorted(k for k in en if k not in referenced)
# Placeholders are reported for information, not as a failure.

# ---------------------------------------------------------------- symbols
symbols_kt = (JAVA / "ui/icons/Symbols.kt").read_text(encoding="utf-8")
declared_symbols = set(re.findall(r"val (\w+): Int", symbols_kt))
used_symbols = {}
for path, text in kotlin_sources():
    for m in re.finditer(r"Symbols\.(\w+)", text):
        used_symbols.setdefault(m.group(1), []).append(path.name)

missing_symbols = sorted(k for k in used_symbols if k not in declared_symbols)
if missing_symbols:
    problems.append(f"Symbols with no declaration: {missing_symbols}")
unused_symbols = sorted(declared_symbols - set(used_symbols))

# ---------------------------------------------------------------- drawables
drawables = {p.stem for p in (RES / "drawable").glob("*.xml")}
declared_drawables = set(re.findall(r"R\.drawable\.(\w+)", symbols_kt))
for name in re.findall(r"R\.drawable\.(\w+)", symbols_kt):
    if name not in drawables:
        problems.append(f"Symbols.kt points at a missing drawable: {name}")
for path in JAVA.rglob("*.kt"):
    for name in re.findall(r"R\.drawable\.(\w+)", path.read_text(encoding="utf-8")):
        if name not in drawables:
            problems.append(f"{path.name} points at a missing drawable: {name}")

# ---------------------------------------------------------------- stale references
stale_terms = [
    "com.hinotes", "MonoLightColorScheme", "MonoDarkColorScheme", "MonoOledDarkColorScheme",
    "ToolbarSplitButton", "SearchResultsOverlay", "picker_preview", "picker_accent_mono",
    "appearance_color_support", "editor_props_chars", "editor_props_lines", "editor_props_id",
    "material_symbols_rounded",
    # Removed with the fingerprint switch and the update check.
    "unlock_fingerprint", "biometric_fingerprint", "BiometricFingerprint", "UpdateChecker",
    "about_update_", "ArchiveHarness", "MarkdownHarness",
]
for term in stale_terms:
    hits = []
    for path, text in kotlin_sources():
        if term in text:
            hits.append(path.name)
    for extra in (EN, ZH):
        if term in extra.read_text(encoding="utf-8"):
            hits.append(extra.name)
    if hits:
        problems.append(f"stale reference to {term!r} in {sorted(set(hits))}")

# ---------------------------------------------------------------- report
print(f"strings: {len(en)} declared, {len(referenced)} referenced, {len(unused)} unused")
if unused:
    print("  unused:", ", ".join(unused))
print(f"symbols: {len(declared_symbols)} declared, {len(used_symbols)} referenced")
if unused_symbols:
    print("  unused:", ", ".join(unused_symbols))
print(f"drawables: {len(drawables)} vectors in res/drawable")
print()

if problems:
    print("PROBLEMS")
    for p in problems:
        print("  -", p)
    sys.exit(1)
print("audit: no problems found")
