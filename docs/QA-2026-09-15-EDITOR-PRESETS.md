# Mobile editor settings scrolling and color presets

- The expanded AI-model settings body is vertically scrollable, capped at 45% of the reported screen height (100–420 dp). Header/collapse control stays outside that scroll region.
- Save/reset controls are stacked to avoid horizontal clipping. Draft palette and preservation preferences survive collapse/reopen via rememberSaveable.
- A bounded, scrollable dropdown supplies eight palette presets: restrained original, classic cel, 1990s anime, shoujo pastel, printed manga inks, Toriyama-inspired palette, Tezuka-inspired palette, and dark fantasy manga.
- Presets fill the editable guide and enable drawing preservation. They do not save or start a paid request automatically: use Save color guide. Artist-inspired options specify palette only, not redrawn characters or linework.
- Compilation checked in the release build. MuMu interaction/keyboard and actual AI output verification remain pending; no claim of pixel-perfect style reproduction.
