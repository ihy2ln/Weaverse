"""Bundles the Adams Haven building art so generated Novel/RPG text can show a building it names.

Reads (read-only) S:/AI/Game/Game Assets/buildings/Unity V1 Stuff:
  - Celestium_Tier_Replacements_v1/catalog.json (names, categories)
  - Celestium_Tier_Replacements_v1/buildings/<id>/tiers/<id>_<tier>.png  (preferred)
  - silverbrook_shelter/base/<id>_<tier>.png, Barn/, Guild Hall/, Shack/   (fallbacks)

Writes app/src/main/assets/images/adams_haven/buildings/<id>_<tier>.webp and catalog.json:
  {"buildings": [{"id", "name", "category", "words": [...], "tiers": {"F": path, ...}}]}

Run:  python tools/build_building_art.py
"""
from __future__ import annotations

import json
from pathlib import Path

from PIL import Image

SRC = Path(r"S:/AI/Game/Game Assets/buildings/Unity V1 Stuff")
OUT = Path(__file__).resolve().parent.parent / "app/src/main/assets/images/adams_haven/buildings"
ASSET = "images/adams_haven/buildings/"
TIERS = ["F", "E", "D", "C", "B", "A", "S", "SS", "SSR"]

# What a writer might call each building, beyond its own name.
WORDS = {
    "shack": ["shack", "hut", "cabin", "shanty", "hovel", "shed"],
    "house": ["house", "dwelling"],
    "cottage": ["cottage"],
    "nursery": ["nursery"],
    "terrace_row": ["terrace", "row houses", "rowhouse", "terrace row"],
    "manor": ["manor", "mansion", "estate"],
    "kitchen": ["kitchen", "cookhouse"],
    "farmstead": ["farmstead", "farmhouse", "farm"],
    "well": ["stone well", "water well", "the well", "a well"],
    "lumber_mill": ["lumber mill", "sawmill", "lumberyard", "mill"],
    "quarry": ["quarry"],
    "barn": ["barn", "stable"],
    "silo": ["silo", "granary"],
    "warehouse": ["warehouse", "storehouse", "depot"],
    "frosted_mug": ["frosted mug", "tavern", "inn", "pub", "alehouse"],
    "guild_hall": ["guild hall", "adventure guild", "guildhall", "guild"],
    "forge": ["forge", "smithy", "blacksmith"],
    "deck_hall": ["deck hall", "training hall", "dojo"],
    "market": ["market", "marketplace", "bazaar"],
    "gate": ["gate", "gatehouse"],
    "heart": ["celestium heart"],
}


def source(art_id: str, tier: str) -> Path | None:
    candidates = [
        SRC / "Celestium_Tier_Replacements_v1/buildings" / art_id / "tiers" / f"{art_id}_{tier}.png",
        SRC / "silverbrook_shelter/base" / f"{art_id}_{tier}.png",
        SRC / "Barn" / f"{art_id}_{tier}.png",
        SRC / "Guild Hall" / f"guild_hall_{tier}.png",
        SRC / "Shack" / f"shack_{tier}.png",
    ]
    if art_id == "guild_hall" and tier == "SSR":
        candidates.append(SRC / "Guild Hall" / "guild_SSR.png")
    return next((p for p in candidates if p.is_file() and (p.parent.name != "Guild Hall" or art_id == "guild_hall")
                 and (p.parent.name != "Shack" or art_id == "shack") and (p.parent.name != "Barn" or art_id == "barn")), None)


def main() -> None:
    catalog = json.loads((SRC / "Celestium_Tier_Replacements_v1/catalog.json").read_text(encoding="utf8"))
    OUT.mkdir(parents=True, exist_ok=True)
    out = []
    for b in catalog["buildings"]:
        art_id = b["art_id"]
        found = {t: p for t in TIERS if (p := source(art_id, t))}
        if not found:
            continue
        # Three is enough to read as "humble, built up, grand": lowest, middle and top tier.
        keys = list(found)
        keep = sorted({keys[0], keys[len(keys) // 2], keys[-1]}, key=TIERS.index)
        tiers = {}
        for t in keep:
            name = f"{art_id}_{t}.webp"
            with Image.open(found[t]) as im:
                im = im.convert("RGBA")
                im.thumbnail((768, 768))
                im.save(OUT / name, "WEBP", quality=80, method=4)
            tiers[t] = ASSET + name
        words = sorted(set(WORDS.get(art_id, [art_id.replace("_", " ")]) + [b["display_name"].lower().removeprefix("the ")]))
        out.append({"id": art_id, "name": b["display_name"], "category": b.get("category", ""), "words": words, "tiers": tiers})
    (OUT / "catalog.json").write_text(json.dumps({"buildings": out}, indent=1), encoding="utf8")
    size = sum(p.stat().st_size for p in OUT.glob("*.webp"))
    print(f"{len(out)} buildings, {len(list(OUT.glob('*.webp')))} pictures, {size / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
