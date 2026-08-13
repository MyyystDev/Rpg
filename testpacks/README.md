# Test datapacks

## myrpg_test

Exercises the custom entity system end-to-end from an external namespace
(`mypack`), the way a modpack creator would.

Install: copy the `myrpg_test` folder into a world's `datapacks/` folder
(already installed in the entities Fabric dev world `Test`), then `/reload`
or re-enter the world.

Contents:
- `mypack:berserker` — hostile melee, scale 1.2, iron axe. Seeds the custom
  stat `mypack:rage` at 60, which lands in the "enraged" stage on spawn:
  +30% speed, +4 attack damage, blaze sound on enter. Drops 1-3 emeralds
  (+1 gold ingot if player-killed) via `mypack:entities/berserker` and 10 XP.
- `mypack:follower` — companion that follows the nearest player (stop at 3
  blocks), opens doors.
- `mypack:zombie_hunter` — guards its spawn point (radius 24), attacks
  zombies on sight, retaliates.
- `mypack:rage` — the stat definition (stored 0-100, stages calm/enraged,
  HUD bar when non-default).

Test checklist:
1. `/myrpg entity list` → should show the 3 mypack + 3 built-in definitions.
2. `/myrpg entity spawn mypack:berserker` → bigger, fast, hits hard, blaze
   sound on spawn; `/myrpg entity inspect` shows rage = 60 (stage: enraged).
3. Kill it → emeralds + XP orbs.
4. `/myrpg entity spawn mypack:follower` → walks to you, stops ~3 blocks.
5. `/myrpg entity spawn mypack:zombie_hunter`, spawn a zombie nearby → it
   attacks the zombie; lure it >24 blocks away → it walks back.
6. Leave and re-enter the world → entities keep definition, name, stats,
   stage effects (speed/damage still boosted), guard anchor.

### Slice 3 additions (interactions + events + rules)
- `mypack:hermit` — right-click him: he greets you (speak action) and his
  hidden `mypack:rage` climbs +20 per click. From 50 the first interaction's
  condition passes instead and he snaps at you. Tests interactions,
  condition gating, ModifyStat on self, and stat persistence across relog.
- `mypack:berserker` — now spawns calm (rage 30). Each hit fires
  `myrpg_entities:entity_hurt` → +10 rage; at 50 he crosses into the
  "enraged" stage mid-fight (blaze sound, +30% speed, +4 damage). On death
  a thunder clap plays. Tests entity_hurt/entity_death rules end-to-end.

### Appearance additions
- Berserker: zombie model + husk texture, hermit: slim humanoid, zombie
  hunter: skeleton model. Built-in models: myrpg_entities:humanoid,
  humanoid_slim, zombie, skeleton. `appearance.texture` accepts any
  resource-pack texture path.

### Ranged combat + AI toolbox additions
- `mypack:bandit_archer` — stray-textured skeleton with a bow: kites you at
  range 15 (arrows via combat type "ranged"), flees when below 35% health.
- Hermit now avoids creepers (avoid_entity goal).
- `/myrpg entity sethome` — stand somewhere, run it, nearest custom entity
  anchors its guard/home position there (persists through save/load).

### Component editor (design book pages 03-11, v1)
- `/myrpg editor entities` → browser. Left-click a row = component editor,
  right-click = context menu (OPEN / SPAWN / DUPLICATE / COPY ID / OPEN
  JSON / DELETE), + NEW ENTITY = create dialog with templates.
- Editor pages: General, Appearance (model cycle + texture + scale),
  Attributes, Movement, Combat (type-dependent fields), AI (read-only
  list), Drops, Advanced (despawn + live JSON). SAVE validates through
  EntityDefinition.CODEC server-side, writes to the world's
  `myrpg_editor` overlay datapack and /reloads — same pipeline as the
  stat editor. DELETE only removes overlay files.
- Quick test: NEW ENTITY → Guard template → name "Test Guard" → SAVE →
  SPAWN. Then edit its max health, SAVE, SPAWN again and inspect.

### Live 3D preview
The entity editor's right pane now renders the actual entity (model,
texture, scale, held items + armor) via the vanilla inventory-preview
pipeline; it follows the mouse and updates live as you edit Appearance
or equipment JSON. Test: open the berserker in the editor, cycle the
model, change scale to 2.0 — the preview updates instantly, before save.

### Entity Wand (in-world placement tool)
`/myrpg entity wand` gives the wand (blaze-rod look). Sneak-right-click to
cycle the selected definition, right-click a block to place it exactly
there facing you, right-click any placed custom entity to open its
definition straight in the editor. All wand actions are gamemaster-gated.

### Equipment page + validation
Editor gains an EQUIPMENT page (six slots, item IDs validated with
green/red dots, mirrored live in the 3D preview) and validation: a
chip in the editor header (N ERROR / N WARN), the full list on the
Advanced page, and SAVE now blocks on errors (unknown items/attributes,
bad texture or loot-table ids) while warnings (combat without targeting,
no AI goals, addon types) just advise.
