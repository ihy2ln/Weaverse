# Writing Guide

## Format menu
- Long-press text (~650 ms) to open Format.
- After dismiss, press the **same highlighted range** to reopen (intentional).
- System `showMenu` callbacks for the same range are ignored so the menu does not loop.
- Collapsing the caret or changing the range clears the gate.

## Color
- Use the HSV wheel or type `#RRGGBB` in Hex.
- Opacity slider when the call site provides it.

## Focus
- Write toolbar **Focus** hides surrounding chrome for distraction-free drafting.
- Shell Focus (Story / Pictures) is separate — gallery vs manuscript canvas.

## Snapshots
- Save named snapshots of the current scene document.
- Restore or delete from the Snapshots UI.
- Stored in `scene_snapshots` (DB v6).

## Media
- Insert pictures/audio via Media controls.
- Long-press media (~400 ms) with haptic for the media menu.
- Stacks reorder via menu actions.
