"""Copies the Adams Haven Card Game's card art, bestiary art, portraits and bestiary.json from the Unity project into
Weaverse's assets, for the native in-app card battle (feature/games/battle).

    python tools/import_cardgame_art.py [path to Unity "Assets/Resources/CardGame"]
"""
import shutil
import sys
from pathlib import Path

SRC = Path(sys.argv[1] if len(sys.argv) > 1 else r"S:\AI\Game\Adams Haven Card Game\Assets\Resources\CardGame")
ROOT = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets"

PLAN = [
    (SRC / "Cards", ROOT / "images" / "adams_haven" / "cards", "*.jpg"),
    (SRC / "Bestiary" / "Cards", ROOT / "images" / "adams_haven" / "bestiary", "*.jpg"),
    (SRC / "Portraits", ROOT / "images" / "adams_haven" / "cardportraits", "*.jpg"),
    (SRC / "Backgrounds", ROOT / "images" / "adams_haven" / "cardbg", "*.jpg"),
]

count = 0
for src, dst, pattern in PLAN:
    dst.mkdir(parents=True, exist_ok=True)
    for f in sorted(src.glob(pattern)):
        shutil.copy2(f, dst / f.name)
        count += 1
(ROOT / "cardgame").mkdir(parents=True, exist_ok=True)
shutil.copy2(SRC / "Bestiary" / "bestiary.json", ROOT / "cardgame" / "bestiary.json")
print(f"copied {count} images + bestiary.json into {ROOT}")
