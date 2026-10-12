#!/usr/bin/env python
"""Generate the bundled Jewish holiday table (Israel) for the Trip tab's day notices.

    python tools/gen_holidays.py            (writes the table for FIRST..LAST below)
    python tools/gen_holidays.py --check    (also compares with pyluach, if installed)

Writes core/src/main/kotlin/il/transit/core/calendar/HolidaysData.kt. No network: the Hebrew
calendar is pure arithmetic (the molad and the four postponement rules, as in Dershowitz &
Reingold, "Calendrical Calculations", ch. 8). Israel's dates: one day of Pesach VII and
Shavuot, Shmini Atzeret without a second day, and the moving rules of the memorial days:
  - Yom HaShoah (27 Nisan): Friday -> Thursday, Sunday -> Monday (Knesset amendment, 1997);
  - Yom HaZikaron / Yom HaAtzmaut (4/5 Iyar): 5 Iyar on Friday or Shabbat -> Wed/Thu before,
    5 Iyar on Monday -> Mon/Tue (Remembrance Day law amendment, 2004).
Re-run with a later LAST when the table runs out (the app says so in Settings from LAST on).
"""
from __future__ import annotations

import datetime as dt
import pathlib
import sys

FIRST, LAST = 2026, 2031
OUT = pathlib.Path(__file__).resolve().parent.parent / "core/src/main/kotlin/il/transit/core/calendar/HolidaysData.kt"

# R.D. (day 1 = 1 Jan 1 CE, proleptic Gregorian, = Python's date ordinal) of 1 Tishrei AM 1, minus 1.
HEBREW_EPOCH = -1373427


def _elapsed_days(year: int) -> int:
    """Days from the epoch to 1 Tishrei of [year] by its molad, after the lo-ADU postponement (never Sun, Wed, Fri)."""
    months = (235 * year - 234) // 19
    parts = 12084 + 13753 * months
    day = months * 29 + parts // 25920
    return day + 1 if (3 * (day + 1)) % 7 < 3 else day


def _correction(year: int) -> int:
    """The GaTaRaD / BeTUTaKPaT rules: keep every year 353-355 or 383-385 days long."""
    ny0, ny1, ny2 = _elapsed_days(year - 1), _elapsed_days(year), _elapsed_days(year + 1)
    if ny2 - ny1 == 356:
        return 2
    if ny1 - ny0 == 382:
        return 1
    return 0


def rosh_hashana(year: int) -> dt.date:
    """1 Tishrei of Hebrew [year], as a civil date."""
    return dt.date.fromordinal(HEBREW_EPOCH + _elapsed_days(year) + _correction(year))


def hebrew_year(holidays_in: int) -> int:
    """The Hebrew year whose 1 Tishrei falls in civil year [holidays_in]."""
    return holidays_in + 3761


def days_for(year: int) -> list[tuple[dt.date, str]]:
    """Every listed day of Hebrew [year] (Tishrei to Sivan), from its Rosh Hashana and the next one."""
    out: list[tuple[dt.date, str]] = []
    rh = rosh_hashana(year)
    d = lambda n: rh + dt.timedelta(days=n)  # noqa: E731  (Tishrei n+1)
    out += [(d(0), "ROSH_HASHANA_1"), (d(1), "ROSH_HASHANA_2"), (d(9), "YOM_KIPPUR"), (d(14), "SUKKOT")]
    out += [(d(n), "CHOL_HAMOED_SUKKOT") for n in range(15, 21)]  # 16-21 Tishrei (21 = Hoshana Rabba)
    out += [(d(21), "SHMINI_ATZERET")]
    # 15 Nisan is always 163 days before the next 1 Tishrei.
    pesach = rosh_hashana(year + 1) - dt.timedelta(days=163)
    p = lambda n: pesach + dt.timedelta(days=n)  # noqa: E731  (Nisan 15+n)
    out += [(p(0), "PESACH_1")] + [(p(n), "CHOL_HAMOED_PESACH") for n in range(1, 6)] + [(p(6), "PESACH_7")]
    out += [(p(50), "SHAVUOT")]  # 6 Sivan
    shoah = p(12)  # 27 Nisan
    if shoah.weekday() == 4:
        shoah -= dt.timedelta(days=1)
    elif shoah.weekday() == 6:
        shoah += dt.timedelta(days=1)
    atzmaut = p(20)  # 5 Iyar (Nisan has 30 days)
    if atzmaut.weekday() == 4:
        atzmaut -= dt.timedelta(days=1)
    elif atzmaut.weekday() == 5:
        atzmaut -= dt.timedelta(days=2)
    elif atzmaut.weekday() == 0:
        atzmaut += dt.timedelta(days=1)
    out += [(shoah, "YOM_HASHOAH"), (atzmaut - dt.timedelta(days=1), "YOM_HAZIKARON"), (atzmaut, "YOM_HAATZMAUT")]
    return out


def table(first: int, last: int) -> list[tuple[dt.date, str]]:
    rows = []
    for y in range(hebrew_year(first) - 1, hebrew_year(last) + 1):
        rows += [r for r in days_for(y) if first <= r[0].year <= last]
    rows.sort()
    dates = [r[0] for r in rows]
    assert len(dates) == len(set(dates)), "two holidays on one day"
    return rows


def check(rows: list[tuple[dt.date, str]]) -> None:
    """Compare the festivals with pyluach's Israel holidays (an independent implementation), when it is installed."""
    try:
        from pyluach import dates, hebrewcal
    except ImportError:
        print("pyluach not installed: skipped the cross-check", file=sys.stderr)
        return
    memorial = {"YOM_HASHOAH", "YOM_HAZIKARON", "YOM_HAATZMAUT"}  # pyluach has no modern days: pinned in HolidaysTest
    ours = {d: n for d, n in rows}
    bad = 0
    day = dt.date(FIRST, 1, 1)
    while day.year <= LAST:
        h = dates.GregorianDate(day.year, day.month, day.day).to_heb()
        name = h.festival(israel=True, include_working_days=True, prefix_day=True)
        mine = ours.get(day)
        mine = None if mine in memorial else mine
        expect = None
        if name:
            if name.startswith("1 Rosh"): expect = "ROSH_HASHANA_1"
            elif name.startswith("2 Rosh"): expect = "ROSH_HASHANA_2"
            elif name == "1 Succos": expect = "SUKKOT"
            elif "Succos" in name: expect = "CHOL_HAMOED_SUKKOT"
            elif name == "Shmini Atzeres": expect = "SHMINI_ATZERET"
            elif name == "1 Pesach": expect = "PESACH_1"
            elif name == "7 Pesach": expect = "PESACH_7"
            elif name == "Pesach Sheni": expect = None
            elif "Pesach" in name: expect = "CHOL_HAMOED_PESACH"
            elif name == "Yom Kippur": expect = "YOM_KIPPUR"
            elif name == "Shavuos": expect = "SHAVUOT"
        if expect != mine and (expect or mine):
            print(f"{day}: pyluach {name!r}, ours {mine}", file=sys.stderr)
            bad += 1
        day += dt.timedelta(days=1)
    if bad:
        sys.exit(f"{bad} days differ from pyluach")
    print("pyluach agrees on every day", file=sys.stderr)


def main() -> None:
    rows = table(FIRST, LAST)
    if "--check" in sys.argv:
        check(rows)
    lines = [
        "package il.transit.core.calendar",
        "",
        "import java.time.LocalDate",
        "",
        "// GENERATED by tools/gen_holidays.py — do not edit by hand.",
        f"// {len(rows)} days, {FIRST}–{LAST}, Israel's dates (one day of yom tov, moving memorial days).",
        "internal object HolidaysData {",
        f"    const val FIRST_YEAR = {FIRST}",
        f"    const val LAST_YEAR = {LAST}",
        "",
        "    val DAYS: Map<LocalDate, Holiday> = listOf(",
    ]
    lines += [f'        "{d.isoformat()}" to Holiday.{n}, // {d.strftime("%a")}' for d, n in rows]
    lines += ["    ).associate { (d, h) -> LocalDate.parse(d) to h }", "}", ""]
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {OUT} ({len(rows)} days)")


if __name__ == "__main__":
    main()
