"""Check every notes-database upgrade path against a real SQLite engine.

`NotesDatabase.onUpgrade` is the one piece of the app that runs against data the user already has,
and the one whose mistakes are unrecoverable: a migration that fails leaves a library that cannot
be opened, and one that drops a column takes notes with it. Nothing else could reach it, because
the helper is an Android class - but the *SQL* is not Android at all, so this runs it.

The statements are read out of `NotesDatabase.kt` rather than copied here: a check that keeps its
own copy of the schema would keep passing while the app's own SQL drifted away from it. The file
has to keep the shape the parser expects - one `execSQL` per `if (oldVersion < N)` - and this fails
loudly when it does not, because a parser that silently matches nothing would report success
forever.

Every older schema is reconstructed by taking today's `CREATE TABLE` and removing the columns that
later migrations add. That is exactly how the older table looked, as long as no column was ever
renamed or dropped, which is a rule this project keeps: notes are the user's data.

    python tools/verify/check_migrations.py
"""

import re
import sqlite3
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SOURCE = REPO / "app/src/main/java/com/hiapps/hinotes/data/NotesDatabase.kt"

failures = []


def check(name, ok, detail=""):
    print(("ok    " if ok else "FAIL  ") + name + (("  " + detail) if detail else ""))
    if not ok:
        failures.append(name)


def fail(message):
    print("FAIL  " + message)
    sys.exit(1)


def read_source():
    if not SOURCE.is_file():
        fail(f"missing {SOURCE}")
    return SOURCE.read_text(encoding="utf-8")


def constants(text):
    """The `const val NAME = "value"` table, as name -> value."""
    table = {}
    for name, value in re.findall(r'const val (\w+)\s*=\s*"([^"]*)"', text):
        table[name] = value
    for name, value in re.findall(r"const val (\w+)\s*=\s*(\d+)", text):
        table[name] = value
    return table


def substitute(sql, table):
    """Resolves the Kotlin string templates the statements are written with."""
    def replace(match):
        name = match.group(1)
        if name not in table:
            fail(f"unknown constant ${name} in SQL: {sql.strip()}")
        return str(table[name])

    return re.sub(r"\$(\w+)", replace, sql)


def section(text, start, *ends):
    body = text.split(start, 1)
    if len(body) != 2:
        fail(f"NotesDatabase.kt has no {start}")
    body = body[1]
    for end in ends:
        body = body.split(end, 1)[0]
    return body


def statements(body, table):
    """Every execSQL argument in [body], raw strings first: both forms appear in the file."""
    out = []
    for raw in re.findall(r'execSQL\(\s*"""([\s\S]*?)"""', body):
        out.append(substitute(raw.strip(), table))
    for plain in re.findall(r'execSQL\(\s*"([^"]+)"', body):
        out.append(substitute(plain, table))
    return out


def migrations(text, table):
    """[(added column, threshold, default, statement, guard constant)] in the order onUpgrade runs."""
    body = section(text, "override fun onUpgrade", "override fun onConfigure")
    steps = []
    for threshold, guard, sql in re.findall(
        r"if \(oldVersion < (\d+)[^{]*?hasColumn\(db, (\w+)\)[^{]*\{\s*"
        r'db\.execSQL\(\s*"([^"]+)"\s*\)',
        body,
    ):
        statement = substitute(sql, table)
        add = re.match(
            r"ALTER TABLE \S+ ADD COLUMN (\w+) (\w+) NOT NULL DEFAULT (\d+)", statement
        )
        if not add:
            fail(f"unrecognised migration statement: {statement}")
        steps.append((add.group(1), int(threshold), int(add.group(3)), statement, guard))
    if not steps:
        fail("no migration statements were found - the parser and the source have drifted apart")
    return steps


def table_parts(create_sql):
    """The CREATE TABLE split into its head, one line per column definition, and its tail."""
    head, rest = create_sql.split("(", 1)
    body, tail = rest.rsplit(")", 1)
    lines = [line.strip().rstrip(",") for line in body.strip().splitlines() if line.strip()]
    return head, lines, tail


def columns_of(lines):
    return [line.split()[0] for line in lines]


def rebuild(head, lines, tail):
    return head + "(\n    " + ",\n    ".join(lines) + "\n)" + tail


def simulate(create_sql, indexes, steps, version, sample):
    """One upgrade: the schema as [version] had it, migrated forward to today."""
    head, lines, tail = table_parts(create_sql)
    drop = {name for name, threshold, _, _, _ in steps if threshold > version}
    current = columns_of(lines)
    kept_lines = [line for line in lines if line.split()[0] not in drop]
    kept = columns_of(kept_lines)
    db = sqlite3.connect(":memory:")
    db.executescript(rebuild(head, kept_lines, tail))
    for index in indexes:
        db.execute(index)
    db.execute(
        "INSERT INTO notes (%s) VALUES (%s)"
        % (", ".join(kept), ", ".join("?" * len(kept))),
        # A column the sample does not name is one of the note's own flags, which is an integer:
        # the note being carried across the upgrade is neither locked nor pinned.
        [sample.get(name, 0) for name in kept],
    )
    for _, threshold, _, statement, _ in steps:
        if threshold > version:
            db.execute(statement)
    db.commit()
    return db, current, kept


def main():
    text = read_source()
    table = constants(text)
    version = int(table.get("DB_VERSION", 0))
    if version < 1:
        fail("DB_VERSION was not found in NotesDatabase.kt")

    statements_in_oncreate = statements(
        section(text, "override fun onCreate", "override fun onUpgrade"), table
    )
    create = next(
        (sql for sql in statements_in_oncreate if sql.upper().startswith("CREATE TABLE")), None
    )
    if create is None:
        fail("no CREATE TABLE in onCreate")
    indexes = [sql for sql in statements_in_oncreate if sql.upper().startswith("CREATE INDEX")]
    if not indexes:
        fail("no CREATE INDEX in onCreate")
    steps = migrations(text, table)

    print(f"schema version {version}, {len(steps)} migration step(s), {len(indexes)} index(es)")
    print()

    for name, threshold, _, _, guard in steps:
        check(
            f"the {name} migration guards the column it adds",
            table.get(guard) == name,
            f"{guard} -> {table.get(guard)!r}",
        )
    print()

    today = columns_of(table_parts(create)[1])
    sample = {
        "id": "keep-me",
        "title": "kept",
        "content": "body",
        "created_at": 11,
        "updated_at": 22,
    }
    for old in range(1, version):
        db, expected, kept = simulate(create, indexes, steps, old, sample)
        added = [name for name, threshold, _, _, _ in steps if threshold > old]
        declared = [default for _, threshold, default, _, _ in steps if threshold > old]
        print(f"upgrade from v{old} (adds {', '.join(added)})")
        actual = [row[1] for row in db.execute("PRAGMA table_info(notes)")]
        # Compared as sets: an added column is appended by ALTER, so its position in the upgraded
        # table is not the position it has in a table created from scratch - and nothing reads a
        # note by position (the helper looks columns up by name).
        check("  today's schema is reached exactly", set(actual) == set(expected), str(actual))
        row = db.execute(
            "SELECT title, content, created_at, updated_at FROM notes WHERE id = 'keep-me'"
        ).fetchone()
        check("  the existing note survived intact", row == ("kept", "body", 11, 22), str(row))
        defaults = db.execute(
            "SELECT %s FROM notes" % ", ".join(added)
        ).fetchone()
        check(
            "  the added columns hold their declared default",
            list(defaults) == declared,
            f"{defaults} vs {declared}",
        )
        found = sorted(
            row[0] for row in db.execute(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND name LIKE 'idx_notes%'"
            )
        )
        check("  the indexes are intact", len(found) == len(indexes), str(found))
        db.close()
        print()

    db, _, _ = simulate(create, indexes, steps, version, sample)
    fresh = [row[1] for row in db.execute("PRAGMA table_info(notes)")]
    check("a fresh install has the same schema as an upgraded one", fresh == today, str(fresh))
    db.close()

    print()
    if failures:
        print("migration check: %d failure(s)" % len(failures))
        sys.exit(1)
    print("migration check: all checks passed")


main()
