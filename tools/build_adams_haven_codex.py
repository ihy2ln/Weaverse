"""Builds Weaverse's bundled Adams Haven Codex pack from the game's current data.

Reads the Unity games (read-only):
  - Adams Haven Card Game: the party fighters (Rules/BuiltinContent.cs) and their portraits.
  - Unity AHCG: JD (Battle/BattleCatalog.cs), the Tower roster (tower_roster.json + CharacterPrompts/*.md),
    the bestiary (bestiary.json + Bestiary/Cards art) and the expedition regions and lair bosses
    (TowerExpedition.cs, bestiary.json "bosses").

Writes:
  app/src/main/assets/codex/adams_haven/pack.json
  app/src/main/assets/codex/adams_haven/img/<entry>[-n].webp

Run:  python tools/build_adams_haven_codex.py
"""
from __future__ import annotations

import datetime
import json
import re
import shutil
from pathlib import Path

from PIL import Image

AHCG = Path(r"S:/AI/Game/Unity AHCG/My project")
CARD_GAME = Path(r"S:/AI/Game/Adams Haven Card Game/Assets")
OUT = Path(__file__).resolve().parent.parent / "app/src/main/assets/codex/adams_haven"
IMG = OUT / "img"
ASSET_PREFIX = "codex/adams_haven/img/"
MAX_SIDE = 900

STATUS_WORDS = {
    "Slow": "slows", "DefenseUp": "raises defense", "DefenseDown": "lowers defense", "AttackUp": "raises attack",
    "AttackDown": "lowers attack", "Regen": "regenerates", "Burn": "burns", "Poison": "poisons", "Stun": "stuns",
    "Taunt": "taunts", "IceCounter": "counters with ice", "Shield": "shields", "Bleed": "bleeds",
}


def image(src: Path, name: str, max_side: int = MAX_SIDE, quality: int = 80) -> str | None:
    """Copies [src] as a phone-sized webp; returns its asset path."""
    if src is None or not src.is_file():
        return None
    IMG.mkdir(parents=True, exist_ok=True)
    out = IMG / f"{name}.webp"
    WRITTEN.add(out.name)
    # Already converted from this exact source: keep it (re-encoding 600 pictures takes a while).
    if out.is_file() and out.stat().st_mtime >= src.stat().st_mtime:
        return ASSET_PREFIX + out.name
    with Image.open(src) as im:
        im = im.convert("RGBA" if im.mode in ("RGBA", "LA", "P") else "RGB")
        im.thumbnail((max_side, max_side))
        im.save(out, "WEBP", quality=quality, method=4)
    return ASSET_PREFIX + out.name


WRITTEN: set[str] = set()

GAME_ASSETS = Path(r"S:/AI/Game/Game Assets/characters")


def fighter_gallery(fid: str) -> list[Path]:
    """A fighter's art from Game Assets/characters/<Name>: renders, outfits, chibis, weapon, card art."""
    folder = next((d for d in GAME_ASSETS.iterdir() if d.is_dir() and d.name.lower() == fid), None)
    if folder is None:
        return []
    picks: list[Path] = []
    picks += sorted(p for p in folder.glob("*.png") if "inspect_texture" not in p.name)
    picks += sorted((folder / "anime-local-v1").glob("anime-front.png"))
    picks += sorted((folder / "chibi-2d-anime-v1").glob("*.png"))
    picks += sorted((folder / "Celestium Weapons").glob("*.png"))
    picks += sorted((folder / "Full").glob("*.png"))
    return picks[:16]


ROSTER_SHOTS = ["card-front", "card", "portrait-bust", "casual", "work-full", "chibi-battle", "chibi-casual",
                "weapon", "tool", "ultimate-cutin", "summon-reveal"]


def roster_gallery(code: str) -> list[Path]:
    """A Tower character's generated art: card, newest bust, outfits, chibi, weapon/tool, ultimate, reveal."""
    # The fullest folder for this code (an early test folder may hold just one card).
    folders = [d for d in (AHCG / "CharacterPrompts/Generated").rglob(f"{code}_*") if d.is_dir()]
    folder = max(folders, key=lambda d: len(list(d.glob("*.png"))), default=None)
    if folder is None:
        return []
    files = {p.stem.split("_", 2)[-1]: p for p in folder.glob("*.png")}
    picks = []
    for shot in ROSTER_SHOTS:
        versions = sorted((k for k in files if k == shot or re.fullmatch(re.escape(shot) + r"-v\d+", k)),
                          key=lambda k: int(k.rsplit("-v", 1)[1]) if "-v" in k else 0)
        if versions:
            picks.append(files[versions[-1]])
    # A card and a card-front are the same shot.
    if any(p.stem.endswith("_card-front") for p in picks):
        picks = [p for p in picks if not p.stem.endswith("_card")]
    return picks[:8]


def args_of(call: str) -> list[str]:
    """Top-level comma-separated arguments of a C# call's text (strings kept quoted)."""
    out, depth, cur, quote = [], 0, "", False
    for ch in call:
        if ch == '"':
            quote = not quote
        if not quote and ch in "([{":
            depth += 1
        if not quote and ch in ")]}":
            depth -= 1
        if not quote and depth == 0 and ch == ",":
            out.append(cur.strip())
            cur = ""
            continue
        cur += ch
    if cur.strip():
        out.append(cur.strip())
    return out


def unq(s: str) -> str:
    return s.strip().strip('"')


def num(s: str) -> float:
    return float(s.strip().rstrip("f"))


def describe_card(a: list[str]) -> str:
    """One line for a card call C/U/A(id, name, owner, ...)."""
    name = unq(a[1])
    named = {k.strip(): v.strip() for k, v in (x.split(":", 1) for x in a[3:] if ":" in x)}
    status = unq(named.get("status", ""))
    bits = []
    if status:
        bits.append(STATUS_WORDS.get(status, status))
    if "heal" in named:
        bits.append(f"heals {num(named['heal']):g}")
    if "draw" in named:
        bits.append(f"draws {named['draw']}")
    target = unq(named.get("target", ""))
    return f"{name}" + (f" ({', '.join(bits)}{'; ' + target if target else ''})" if bits or target else "")


# ---------------------------------------------------------------- fighters

def fighters() -> list[dict]:
    src = (CARD_GAME / "Scripts/Rules/BuiltinContent.cs").read_text(encoding="utf8")
    entries = []
    for block in re.split(r"\n\s*h = Hero\(", src)[1:]:
        head = block.split(");", 1)[0]
        a = args_of(head)
        fid, name, title, role, element = (unq(x) for x in a[:5])
        tier, hp, atk, mag, de, res, spd = (num(x) for x in a[5:12])
        bio = unq(a[-1])
        body = block.split("list.Add(h)", 1)[0]
        cards = [describe_card(args_of(m)) for m in re.findall(r"\bC\((.*?)\)(?:,|\s*\})", body)]
        ults = [unq(args_of(m)[1]) for m in re.findall(r"\bU\((.*?)\)(?:,|\s*\})", body)]
        awake = [unq(args_of(m)[1]) for m in re.findall(r"\bA\((.*?)\);", body)]
        portrait = image(CARD_GAME / f"Resources/CardGame/Portraits/{fid}.jpg", f"fighter-{fid}")
        gallery = [image(p, f"fighter-{fid}-{i}", 760, 76) for i, p in enumerate(fighter_gallery(fid))]
        text = (
            f"{name}, the {title}. {bio}\n\n"
            f"Party fighter in Adams Haven: {role} · {element} · tier {tier:g}.\n"
            f"HP {hp:g} · Attack {atk:g} · Magic {mag:g} · Defense {de:g} · Resistance {res:g} · Speed {spd:g}\n\n"
            f"Cards: " + "; ".join(cards) + "\n"
            f"Ultimates: " + ", ".join(ults) + ("\nAwakening: " + ", ".join(awake) if awake else "")
        )
        entries.append({
            "id": f"fighter-{fid}", "category": "Characters", "name": name,
            "aliases": [name.split()[0], title] if " " in name else [title],
            "text": text, "images": [p for p in [portrait] + gallery if p],
            "character": {
                "characterClass": title, "subclass": f"{role} · {element}", "species": "",
                "backstoryAndPersonality": bio, "featuresAndTraits": "Ultimates: " + ", ".join(ults),
                "attacksAndActions": "\n".join(cards), "maxHp": int(hp),
            },
        })
    # JD, the summoner (Battle catalog).
    cat = (AHCG / "Assets/Scripts/Battle/BattleCatalog.cs").read_text(encoding="utf8")
    kit = [describe_card(args_of(m)) for m in re.findall(r'\bC\(("sm_.*?)\),', cat)]
    portrait = image(CARD_GAME / "Resources/CardGame/Portraits/jd.jpg", "fighter-jd")
    entries.append({
        "id": "fighter-jd", "category": "Characters", "name": "JD", "aliases": ["The Summoner"],
        "text": "JD, the summoner who leads the party in Adams Haven battles. Light · 260 HP · Defense 14 · Resistance 14.\n\n"
                "Summoner kit: " + "; ".join(kit),
        "images": [p for p in [portrait] if p],
        "character": {"characterClass": "Summoner", "subclass": "Light", "attacksAndActions": "\n".join(kit), "maxHp": 260},
    })
    return entries


# ---------------------------------------------------------------- roster

def sheet_rows(md: str) -> dict[str, str]:
    rows = {}
    for line in md.splitlines():
        m = re.match(r"\|\s*([^|]+?)\s*\|\s*(.+?)\s*\|$", line)
        if m and m.group(1) not in ("Field", "---"):
            rows[m.group(1).strip("* ")] = re.sub(r"\*\*", "", m.group(2))
    return rows


def roster() -> list[dict]:
    units = json.loads((AHCG / "Assets/Resources/AdamsHaven/Roster/tower_roster.json").read_text(encoding="utf8"))["units"]
    md_dirs = [AHCG / "CharacterPrompts/heroes", AHCG / "CharacterPrompts/residents"]
    gen = AHCG / "CharacterPrompts/Generated"
    entries = []
    for u in units:
        code = u["code"]
        md = next((p for d in md_dirs for p in d.glob(f"{code}_*.md")), None)
        rows = sheet_rows(md.read_text(encoding="utf8")) if md else {}
        images = [p for p in (image(src, f"roster-{u['id']}-{i}", MAX_SIDE if i == 0 else 760, 80 if i == 0 else 76)
                              for i, src in enumerate(roster_gallery(code))) if p]
        stats = u.get("stats") or {}
        stat_line = " · ".join(f"{k.title()} {v}" for k, v in stats.items()) if isinstance(stats, dict) else ""
        kind = "Tower hero" if u["kind"] == "hero" else "Haven resident"
        abilities = [u.get(k, "") for k in ("passive", "ability1", "ability2", "ability3", "ultimate")]
        abilities = [re.sub(r"\s*Visual:.*$", "", a) for a in abilities if a]
        lines = [
            f"{u['name']}, {u['epithet']}. {kind} of rank {u['rankName']}.",
            f"{u['race']} · {u['gender']} · {u['age']} · {u['element']} · {u['cls']} ({u['role']}).",
            u.get("personality", ""),
            f"\u201c{u['quote']}\u201d" if u.get("quote") else "",
            "Origin: " + u.get("origin", "") if u.get("origin") else "",
            f"Weapon: {u['weapon']} \u2014 {u.get('weaponDesc', '')}" if u.get("weapon") else "",
            f"Tool: {u['tool']} \u2014 {u.get('toolDesc', '')}" if u.get("tool") else "",
            "Perk: " + u["perk"] if u.get("perk") else "",
            "Abilities: " + " / ".join(abilities) if abilities else "",
            f"Tower room: {u.get('room', '')} · primary {u.get('primary', '')}, secondary {u.get('secondary', '')}" if u.get("room") else "",
            "Stats: " + stat_line if stat_line else "",
            "Appearance: " + rows["Visual anchors"] if rows.get("Visual anchors") else "",
        ]
        appearance = "\n".join(f"{k}: {rows[k]}" for k in ("Visual anchors", "Battle outfit", "Casual outfit", "Work outfit") if rows.get(k))
        entries.append({
            "id": f"roster-{u['id']}", "category": "Characters", "name": u["name"],
            "aliases": [u["name"].split()[0]] if " " in u["name"] else [],
            "text": "\n".join(x for x in lines if x),
            "images": images,
            "character": {
                "characterClass": u.get("cls", ""), "subclass": f"{u.get('role', '')} · {u.get('element', '')}",
                "species": u.get("race", ""), "size": u.get("size", "Medium"),
                "appearance": appearance,
                "bodyDescription": f"{u.get('body', '')}. {u.get('height', '')} cm, {u.get('weight', '')} kg; "
                                   f"{u.get('chest', '')}/{u.get('waist', '')}/{u.get('hips', '')} cm.",
                "backstoryAndPersonality": " ".join(x for x in [u.get("personality", ""), u.get("origin", "")] if x),
                "featuresAndTraits": "\n".join(abilities + ([u["perk"]] if u.get("perk") else [])),
                "weaponsAndDamageCantrips": f"{u.get('weapon', '')}: {u.get('weaponDesc', '')}" if u.get("weapon") else "",
                "resourcesAndTools": f"{u.get('tool', '')}: {u.get('toolDesc', '')}" if u.get("tool") else "",
            },
        })
    return entries


# ---------------------------------------------------------------- bestiary

def bestiary() -> tuple[list[dict], dict]:
    data = json.loads((AHCG / "Assets/Resources/AdamsHaven/Bestiary/bestiary.json").read_text(encoding="utf8"))
    ranks = data["ranks"]
    cards = AHCG / "Assets/Resources/AdamsHaven/Bestiary/Cards"
    entries, by_id = [], {}
    for fam in data["families"]:
        span = lambda lo, hi: ranks[lo - 1] if lo == hi else f"{ranks[lo - 1]}\u2013{ranks[hi - 1]}"
        forms_text, images = [], []
        for i, form in enumerate(fam["forms"]):
            moves = ", ".join(c["name"] for c in form.get("cards", []))
            drops = ", ".join(form.get("drops", []))
            forms_text.append(
                f"\u2022 {form['name']} ({span(form['minRank'], form['maxRank'])}): {form.get('flavor', '')}"
                + (f" Moves: {moves}." if moves else "") + (f" Drops: {drops}." if drops else "")
                + (f" Evolves into {form['evolvesTo'].replace('_', ' ').title()}." if form.get("evolvesTo") else "")
            )
            art = cards / f"{form.get('art') or form['id']}_card.jpg"
            if not art.is_file():
                # Forms still on shared art: their own painted card from MonsterPrompts.
                art = AHCG / "MonsterPrompts" / fam["id"] / form["id"] / "playing_card.png"
            if len(images) < 8:
                p = image(art, f"monster-{fam['id']}-{i}")
                if p:
                    images.append(p)
        text = (
            f"{fam['name']}: {fam['element']} {fam['role'].lower()}s, ranks {span(fam['minRank'], fam['maxRank'])}"
            + (" (large)" if fam.get("large") else "") + ".\n\n"
            + fam.get("lore", "") + "\n\n"
            + f"Material: {fam.get('material', '')}. Found in: {', '.join(fam.get('themes', []))}.\n\n"
            + "Forms:\n" + "\n".join(forms_text)
        )
        entry = {
            "id": f"monster-{fam['id']}", "category": "Monsters", "name": fam["name"],
            "aliases": [f["name"] for f in fam["forms"]],
            "text": text, "images": images,
            "other": {
                "subtype": f"{fam['element']} {fam['role']} · ranks {span(fam['minRank'], fam['maxRank'])}",
                "summary": fam.get("lore", ""),
                "description": text,
                "details": "\n".join(forms_text),
                "customFields": [
                    {"label": "Element", "value": fam["element"]}, {"label": "Role", "value": fam["role"]},
                    {"label": "Ranks", "value": span(fam["minRank"], fam["maxRank"])},
                    {"label": "Material", "value": fam.get("material", "")},
                    {"label": "Inspired by", "value": fam.get("inspiration", "")},
                ],
            },
        }
        entries.append(entry)
        by_id[fam["id"]] = entry
    return entries, data


# ---------------------------------------------------------------- regions

def regions(bestiary_data: dict, monsters: dict) -> list[dict]:
    src = (AHCG / "Assets/Scripts/TowerExpedition.cs").read_text(encoding="utf8")
    bosses = {b["region"]: b for b in bestiary_data.get("bosses", [])}
    names = {}
    out = []
    calls = []
    for start in [m.end() for m in re.finditer(r"new TowerRegionDef\(", src)]:
        depth, i = 1, start
        while depth:
            depth += {"(": 1, ")": -1}.get(src[i], 0)
            i += 1
        calls.append(src[start:i - 1])
    for call in calls:
        a = args_of(call)
        rid, name, blurb = unq(a[0]), unq(a[1]), unq(a[2])
        depth, boss_depth = int(num(a[5])), int(num(a[6]))
        requires = [names.get(unq(x), unq(x)) for x in a[8:]]
        names[rid] = name
        boss = bosses.get(rid)
        fam = monsters.get(boss["family"]) if boss else None
        text = (
            f"{name}: {blurb}\n\nExpedition region, depth {depth}; its lair boss waits at depth {boss_depth}."
            + (f"\nLair boss: {boss['title']} ({fam['name'] if fam else boss['family']})." if boss else "")
            + (f"\nReached from: {', '.join(requires)}." if requires else "")
        )
        out.append({
            "id": f"region-{rid}", "category": "Locations", "name": name, "aliases": [rid.replace("_", " ").title()],
            "text": text, "images": fam["images"][-1:] if fam else [],
            "location": {
                "locationType": "Expedition region", "region": "Silverwood" if rid.startswith("silverwood") else "Silverbrook",
                "description": blurb, "hooks": f"Lair boss: {boss['title']}" if boss else "",
                "notes": text,
            },
        })
    return out


def main() -> None:
    monster_entries, data = bestiary()
    entries = fighters() + roster() + monster_entries + regions(data, {e["id"].removeprefix("monster-"): e for e in monster_entries})
    pack = {
        "format": "weaverse-codex-pack",
        "id": "adams-haven",
        "version": datetime.datetime.now().strftime("%Y-%m-%dT%H%M") + "." + str(len(entries)),
        "source": "Adams Haven (Unity AHCG + Card Game)",
        "entries": entries,
    }
    for stale in IMG.glob("*.webp"):
        if stale.name not in WRITTEN:
            stale.unlink()
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "pack.json").write_text(json.dumps(pack, ensure_ascii=False, indent=1), encoding="utf8")
    size = sum(p.stat().st_size for p in IMG.glob("*.webp"))
    print(f"{len(entries)} entries, {len(list(IMG.glob('*.webp')))} images, {size / 1e6:.1f} MB -> {OUT}")


if __name__ == "__main__":
    main()
