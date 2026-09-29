# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later
"""Make a font's Russian Cyrillic forms the default glyphs.

Sofia Sans draws Bulgarian Cyrillic forms by default and exposes the Russian forms only through
the `locl` feature of the `cyrl/RUS` language system. Compose on desktop and the web does not pass
the text locale to the shaper, so this script points the character map of every codepoint that
the RUS `locl` lookup substitutes directly at its Russian glyph. Other glyphs and features stay
unchanged. Usage: `uv run --with fonttools python3 tools/fonts/russian_cyrillic_defaults.py FONT...`
"""

import sys

from fontTools.ttLib import TTFont


def russian_locl_mapping(font: TTFont) -> dict[str, str]:
    gsub = font["GSUB"].table
    features = gsub.FeatureList.FeatureRecord
    mapping: dict[str, str] = {}
    for script in gsub.ScriptList.ScriptRecord:
        if script.ScriptTag != "cyrl":
            continue
        for language in script.Script.LangSysRecord:
            if language.LangSysTag != "RUS ":
                continue
            for index in language.LangSys.FeatureIndex:
                if features[index].FeatureTag != "locl":
                    continue
                for lookup_index in features[index].Feature.LookupListIndex:
                    lookup = gsub.LookupList.Lookup[lookup_index]
                    if lookup.LookupType != 1:
                        raise ValueError(f"unexpected RUS locl lookup type {lookup.LookupType}")
                    for subtable in lookup.SubTable:
                        mapping.update(subtable.mapping)
    return mapping


def remap(path: str) -> int:
    font = TTFont(path)
    mapping = russian_locl_mapping(font)
    changed = 0
    for table in font["cmap"].tables:
        if not table.isUnicode():
            continue
        for codepoint, glyph in list(table.cmap.items()):
            if 0x0400 <= codepoint <= 0x04FF and glyph in mapping:
                table.cmap[codepoint] = mapping[glyph]
                changed += 1
    font.save(path)
    return changed


if __name__ == "__main__":
    for font_path in sys.argv[1:]:
        print(f"{font_path}: {remap(font_path)} cmap entries now use the Russian forms")
