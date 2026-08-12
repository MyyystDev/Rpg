# Custom Entities In-Game Editor

## Overview

The **Custom Entities In-Game Editor** lets modpack creators create, modify, test, spawn, and debug custom entities without manually editing JSON.

The JSON remains the source of truth:

```text
JSON File
    ↕
Entity Definition
    ↕
In-Game Editor
```

The same editor should support simple NPCs, merchants, quest givers, guards, hostile mobs, companions, ambient creatures, training dummies, and bosses.

The recommended workflow is:

```text
ENTITY LIST
     ↓
COMPONENT / PROPERTY EDITOR
     ↓
SPECIALIZED VISUAL EDITORS
     ↓
NODE GRAPH FOR ADVANCED LOGIC
```

The editor should be easy for simple entities while still exposing advanced tools when creators need them.

---

# Design Goals

The editor should be:

- Fast for simple NPC creation
- Consistent with the rest of RPG Framework
- Compatible with manual JSON editing
- Component-based
- Highly visual
- Extensible through registries
- Safe against invalid configuration
- Useful for live testing and debugging

The most important UX rule is:

> The creator should only see the complexity relevant to the entity they are creating.

A merchant should not need to see boss settings. A decorative NPC should not need combat settings. A boss should be able to add variables, rules, custom attacks, and advanced AI.

---

# Main Entity Browser

The first screen lists every custom entity in the project.

```text
┌──────────────────────────────────────────────────────────────┐
│ Custom Entities                                    [+ New] │
├──────────────────────────────────────────────────────────────┤
│ Search [________________________]        Filter [All ▼]      │
│                                                              │
│ 👤 Village Blacksmith                                       │
│    mypack:blacksmith                       NPC • Merchant    │
│                                                              │
│ ⚔ Bandit                                                    │
│    mypack:bandit                           Hostile           │
│                                                              │
│ ☠ Hollow King                                               │
│    mypack:hollow_king                      Boss              │
│                                                              │
│                                       [Import] [Reload]      │
└──────────────────────────────────────────────────────────────┘
```

Each entity entry can show:

- Icon or head preview
- Display name
- Resource ID
- Component/category tags
- Validation errors or warnings
- Unsaved state

Possible actions:

- Open
- Duplicate
- Rename
- Spawn
- Copy ID
- Open JSON
- Delete

Useful filters include All, NPC, Hostile, Merchant, Quest, Boss, Companion, Invalid, and Recently Edited.

---

# Creating an Entity

Pressing **New** opens a lightweight creation dialog.

```text
Create Custom Entity

Name
[ Village Guard                ]

ID
[ mypack:village_guard         ]

Template
[ Guard ▼                      ]

Model
[ Humanoid ▼                   ]

             [Cancel] [Create]
```

The creator should only need the minimum required information before the entity exists.

---

# Templates

Recommended templates:

- Blank Entity
- NPC
- Merchant
- Quest Giver
- Guard
- Hostile Mob
- Ranged Enemy
- Companion
- Boss
- Training Dummy
- Ambient Creature

Templates are only starting configurations. They do not represent different Java entity classes.

For example, a Merchant template might add:

```text
Appearance
Attributes
Movement
AI
Interaction
Shop
Persistence
```

A Boss template might add:

```text
Appearance
Attributes
Movement
AI
Combat
Boss Bar
Variables
Rules
Persistence
```

---

# Main Entity Editor

The main editor should have three major areas:

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│ Village Blacksmith                                      [Spawn] [Save]      │
├───────────────┬──────────────────────────────────────┬───────────────────────┤
│ General       │                                      │                       │
│ Appearance    │                                      │      3D PREVIEW       │
│ Attributes    │          PROPERTY EDITOR             │                       │
│ Movement      │                                      │       Blacksmith      │
│ AI            │                                      │                       │
│ Combat        │                                      │       ↻ drag          │
│ Equipment     │                                      │                       │
│ Interaction   │                                      ├───────────────────────┤
│ Faction       │                                      │ Preview Options       │
│ Drops         │                                      │ Day / Night           │
│ Spawning      │                                      │ Idle / Walk / Attack  │
│ Rules         │                                      │ Scale: 100%           │
│ Advanced      │                                      │                       │
└───────────────┴──────────────────────────────────────┴───────────────────────┘
```

The left side contains components and pages, the center edits properties, and the right side keeps a live entity preview visible.

---

# Component-Based Editor

The editor should mirror the component-based entity architecture.

```text
Components
──────────────────────

✓ Appearance
✓ Attributes
✓ Movement
✓ AI
✓ Interaction
✓ Shop

+ Add Component
```

A decorative NPC may only use Appearance and Interaction. A merchant may add AI, Shop, and Faction. A boss may add Combat, Boss Bar, Variables, Rules, and Drops.

This prevents the editor from becoming a giant list of hundreds of fields.

---

# Add Component Screen

```text
Add Component

Search [________________________]

Behavior
────────────────────
🧠 AI
🏃 Movement
⚔ Combat

RPG
────────────────────
💬 Dialogue
💰 Shop
📜 Quest
🏳 Faction

World
────────────────────
🌍 Spawning
🏠 Home
🗺 Patrol

Advanced
────────────────────
⚡ Rules
🔢 Variables
👑 Boss
```

Components should be registry-driven so addons can register their own components and editor widgets.

---

# General Page

```text
General

Name
[ Village Blacksmith                     ]

ID
[ mypack:blacksmith                      ]

Description
[ The village's local blacksmith...       ]

Tags
[ village ] [ merchant ] [ + ]

Nameplate
[✓] Visible

Nameplate Distance
[ 32 ]
```

Changing an ID should display references before saving.

```text
⚠ This entity is referenced by:

3 quests
2 dialogues
1 spawn rule
```

---

# Appearance Editor

The Appearance page should be highly visual.

```text
Appearance

Model
[ Humanoid ▼ ]

Texture
[ blacksmith.png              ] [Browse]

Scale
[ 1.00 ]

Hitbox
Width   [ 0.60 ]
Height  [ 1.95 ]

Eye Height
[ Auto ]

Shadow
[ 0.50 ]

[✓] Render armor
[✓] Render held items
```

Possible settings include model, texture, texture variants, scale, hitbox, eye height, shadow size, tint, transparency, render layer, glow, and equipment rendering.

All visual changes should update the preview immediately.

---

# Live 3D Preview

A permanent 3D preview is one of the most important parts of this editor.

Recommended controls:

```text
Left Drag      Rotate
Scroll         Zoom
Shift + Drag   Pan
```

Preview state buttons:

```text
[Idle]
[Walk]
[Run]
[Attack]
[Hurt]
[Death]
[Interact]
```

Additional preview options could include lighting, background, scale, animation state, and texture variant.

---

# Texture Variants

```text
Texture Variants

blacksmith_1.png         Weight [5]   [Preview] [X]
blacksmith_2.png         Weight [2]   [Preview] [X]
blacksmith_old.png       Weight [1]   [Preview] [X]

                                  [+ Variant]
```

The preview should support Previous, Next, and Randomize controls.

---

# Model Selection

The model picker should support built-in, custom, and addon model providers.

```text
Built-In
────────────────────
Humanoid
Zombie
Skeleton
Villager
Quadruped
Slime
Floating

Custom
────────────────────
mypack:golem
mypack:hollow_king

Addons
────────────────────
Custom Animation Model
```

---

# Attributes Page

```text
Attributes

Max Health              [ 30       ]
Movement Speed          [ 0.22     ]
Attack Damage           [ 4        ]
Armor                   [ 2        ]
Armor Toughness         [ 0        ]
Knockback Resistance    [ 0        ]
Follow Range            [ 24       ]

                            [+ Attribute]
```

Only explicitly configured attributes should appear by default.

---

# Damage Configuration

Advanced entities may define damage modifiers.

```text
Damage Modifiers

Fire Damage               × [2.0]
Projectile Damage         × [1.5]
Magic Damage              × [0.5]
Fall Damage               [Immune]

                            [+ Damage Rule]
```

Possible behavior includes multipliers, immunity, flat reduction, and conditional modifiers.

---

# Movement Editor

```text
Movement

Movement Type
[ Ground ▼ ]

Speed
[ Use movement attribute ]

[✓] Can jump
[✓] Can swim
[✓] Can open doors
[ ] Can fly
[ ] Avoid water

Step Height
[ Default ]
```

Possible movement modes:

- Ground
- Flying
- Swimming
- Hovering
- Stationary

Type-specific settings should only appear when relevant.

---

# AI Editor

Basic AI should use a prioritized list rather than a node graph.

```text
AI Goals

Priority
────────────────────────────────────────────

1   ⚔ Melee Attack                       [Edit]
2   🏠 Return To Home                    [Edit]
3   🚶 Random Wander                     [Edit]
4   👁 Look At Player                    [Edit]
5   👀 Random Look Around                [Edit]

                     [+ Add Goal]
```

Goals should be draggable to reorder priority.

Possible goals include Random Wander, Follow Player, Follow Owner, Guard Position, Patrol, Return Home, Look At Player, Avoid Entity, Melee Attack, Ranged Attack, Flee At Low Health, Sit, Sleep, and Stay Still.

---

# AI Goal Configuration

Example:

```text
Random Wander

Speed
[ 0.8 ]

Interval
[ 120 ticks ]

Maximum Distance
[ 12 blocks ]

Conditions
No conditions

                         [+ Condition]

             [Cancel] [Save]
```

---

# Targeting Editor

Target selection should be separate from normal AI goals.

```text
Target Selection

1. Retaliate When Attacked
2. Attack Faction Enemies
3. Attack Player
      Condition:
      Bandit Reputation <= -25

                           [+ Target Rule]
```

This separates **who should I target?** from **what should I do with that target?**

Possible target rules include player, faction enemy, entity type, entity tag, retaliation, defend owner, and conditional targeting.

---

# Home Editor

```text
Home

Position
X [ 124 ]
Y [ 68  ]
Z [ -42 ]

Radius
[ 16 ]

Return Distance
[ 24 ]

[Use Current Position]
[Select In World]
```

Positions should be selectable directly in the world whenever possible.

---

# Patrol Editor

```text
Patrol

Mode
[ Loop ▼ ]

Waypoints

1   West Gate       Wait: 40 ticks
2   Marketplace     Wait: 100 ticks
3   East Gate       Wait: 40 ticks

                            [+ Waypoint]
```

Possible patrol modes include Loop, Reverse, Random, and Once.

Each waypoint can optionally define wait time, facing direction, actions on arrival, and conditions.

---

# In-World Patrol Editing

The editor should provide:

```text
[Edit Patrol In World]
```

Suggested controls:

```text
Left Click
Add waypoint

Right Click
Remove waypoint

Mouse Wheel
Select waypoint

Enter
Save patrol

Escape
Cancel
```

Visual lines should connect the points while editing.

---

# Schedule Editor

Schedules can create NPC daily routines.

```text
NPC Schedule

00:00      06:00      12:00      18:00      24:00
|-----------|-----------|-----------|-----------|
 Sleep       Farm        Tavern      Home

06:00  → Move to Farm
12:00  → Move to Tavern
18:00  → Move Home
22:00  → Sleep

                                [+ Entry]
```

Possible actions include moving to a location, patrolling, sleeping, changing dialogue, opening a shop, closing a shop, changing variables, and running generic actions.

---

# Equipment Editor

The equipment editor should visually resemble Minecraft's inventory.

```text
        ┌───────┐
Head    │ Helmet│
        └───────┘

        ┌───────┐
Chest   │ Chest │
        └───────┘

        ┌───────┐
Legs    │ Legs  │
        └───────┘

        ┌───────┐
Feet    │ Boots │
        └───────┘

Main Hand       Off Hand
┌──────────┐   ┌──────────┐
│ Iron Axe │   │          │
└──────────┘   └──────────┘
```

Creators should be able to drag actual items into slots where possible.

Advanced slots can support weighted random equipment, drop chance, rendering visibility, and item components.

---

# Combat Editor

```text
Combat Type

(o) None
( ) Melee
( ) Ranged
( ) Custom
```

The selected type controls which settings appear.

## Melee

```text
Attack Range
[ 2.5 ]

Attack Cooldown
[ 20 ticks ]

Movement Speed
[ 1.0 ]

Knockback
[ 0.0 ]
```

## Ranged

```text
Projectile
[ minecraft:arrow ▼ ]

Range
[ 20 ]

Cooldown
[ 30 ticks ]

Projectile Speed
[ 1.5 ]

Accuracy
[ 90% ]

Burst
[ 1 ]
```

---

# Custom Attack Editor

Advanced attacks can use an action sequence or graph.

```text
Fire Slam
────────────────────────────

[Play Animation: slam]
          ↓
[Wait 10 ticks]
          ↓
[Play Sound]
          ↓
[Spawn Flame Particles]
          ↓
[Damage Radius 5]
```

Custom attacks can then be referenced by AI goals.

---

# Interaction Editor

Interactions should support simple form-based configuration.

```text
Interactions

1. Right Click
   → Open Dialogue: blacksmith_intro

2. Sneak + Right Click
   → Open Shop: blacksmith_shop

3. Right Click
   IF Quest "lost_hammer" completed
   → Dialogue: blacksmith_thanks

                         [+ Interaction]
```

Each interaction follows:

```text
Trigger
Conditions
Actions
```

A full node graph should only be necessary for complicated interactions.

---

# Dialogue, Shop, Quest, Faction, and Ability Integration

References to other RPG Framework systems should use resource pickers.

```text
Dialogue
[ mypack:blacksmith_intro ▼ ]

[Edit Dialogue]
```

```text
Shop
[ mypack:blacksmith_shop ▼ ]

[Edit Shop]
```

```text
Faction
[ mypack:village ▼ ]

[Edit Faction]
```

The editor should let creators jump directly to referenced objects instead of closing one editor and manually finding another.

---

# Drops Editor

Minecraft loot tables should be reused whenever possible.

```text
Drops

Loot Table
[ mypack:entities/bandit ▼ ]

[Edit Loot Table]

Minecraft XP
[ 10 ]

RPG XP
[ 25 ]
```

---

# Sounds Editor

```text
Sounds

Ambient
[ minecraft:entity.villager.ambient ]

Hurt
[ minecraft:entity.villager.hurt ]

Death
[ minecraft:entity.villager.death ]

Attack
[ None ]

Interact
[ None ]

[▶ Preview]
```

---

# Spawning Editor

```text
Spawning

[✓] Can Spawn Naturally

Weight
[ 10 ]

Group Size
Min [2]
Max [5]

Dimensions
[ minecraft:overworld ]

Biomes
[ #minecraft:is_forest ]

Light
Min [0]
Max [7]

Height
Min [-64]
Max [320]

Conditions
                     [+ Condition]
```

Possible spawn conditions include dimension, biome, height, light, time, weather, region, difficulty, nearby blocks, distance from player, and custom conditions.

---

# Spawn Testing

The spawning editor should include:

```text
[Spawn Test Entity]
[Spawn Test Group]
```

A debug tool could explain failed spawn conditions:

```text
Spawn Test

✓ Correct biome
✓ Correct dimension
✗ Light level too high
✓ Height valid

Entity cannot spawn here.
```

---

# Persistence Editor

```text
Persistence

Despawn
[ Never ▼ ]

Save Instance State
[✓]

Unique Entity
[ ]

Respawning
[ Disabled ▼ ]
```

Possible options include never despawn, vanilla-like despawn, distance-based despawn, delayed respawn, next-day respawn, and region-reset respawn.

---

# Unique Entities

```text
Unique Entity
[✓]

Unique ID
[ village_blacksmith ]

Prevent Duplicate Spawn
[✓]
```

This is useful for important story NPCs and bosses.

---

# Variables Editor

Advanced entities should support per-instance variables.

```text
Variables

Name              Type          Default

phase             Integer       1
angry             Boolean       false
player_met        Boolean       false
ritual_progress   Integer       0

                            [+ Variable]
```

Variables should become available automatically in conditions, actions, interactions, rules, boss phases, and dialogues.

---

# Rules Overview

Rules should first appear as a readable list.

```text
Rules

Phase Two
Health <= 50%
→ Set phase = 2
→ Summon minions

Become Angry
Player attacks entity
→ angry = true

Night Dialogue
Time >= 13000
→ Set dialogue night_dialogue

                                  [+ Rule]
```

Clicking a rule opens the advanced graph editor.

---

# Rule Graph Editor

Rules use the shared RPG Framework node system.

```text
┌───────────────────┐
│ Health Changed    │
└─────────●─────────┘
          │
          ▼
┌───────────────────┐
│ Health <= 50%     │
└─────────●─────────┘
          │
          ▼
┌───────────────────┐
│ Variable phase=1  │
└─────────●─────────┘
          │
          ▼
┌───────────────────┐
│ Set phase = 2     │
└─────────●─────────┘
          │
          ▼
┌───────────────────┐
│ Spawn Minions     │
└───────────────────┘
```

The same graph style should be reused by stats, quests, dialogue, abilities, zones, and other framework systems.

---

# Boss Component

A boss should not be a separate entity type.

Adding a Boss component exposes boss-specific presentation settings.

```text
Boss

[✓] Boss Bar

Name
[ The Hollow King ]

Color
[ Purple ▼ ]

Style
[ Progress ▼ ]

Visibility
[ 64 blocks ]

[✓] Darken Sky
[ ] Create Fog

Boss Music
[ mypack:hollow_king_theme ]
```

Boss phases should be built using variables, rules, AI, and custom attacks.

---

# Advanced Page

```text
Advanced

JSON File
data/mypack/rpg/entities/blacksmith.json

[View JSON]
[Reload From Disk]

References
17 references

[View References]

Validation
✓ No errors

Debug
[Inspect Runtime]
```

Advanced tools may include JSON preview, raw editing, reference viewer, validation, runtime debugging, internal IDs, and data reload controls.

---

# JSON Preview

The JSON remains the actual entity definition.

```json
{
  "id": "mypack:blacksmith",
  "appearance": {
    "model": "rpg:humanoid",
    "texture": "mypack:textures/entity/blacksmith.png"
  },
  "attributes": {
    "minecraft:max_health": 30
  }
}
```

A read-only preview is enough for the first version. Raw editing can be added later with validation, syntax highlighting, undo, and safe rollback.

---

# Entity Definition vs Entity Instance

The editor must clearly distinguish between a reusable definition and one placed entity instance.

```text
Definition:
mypack:village_guard
```

can be used by many entities.

An individual instance may contain overrides:

```text
Name = West Gate Guard
Home = West Gate
Patrol = west_gate_patrol
```

---

# Instance Editor

Looking at a placed custom entity and opening the editor should show:

```text
Editing Instance

Definition
mypack:village_guard

Instance UUID
3c18...

Overrides
────────────────────────

Name
West Gate Guard

Home
West Gate

Patrol
west_gate

                    [+ Override]

[Edit Definition]
```

Useful instance overrides include display name, home, patrol, dialogue, shop, equipment, faction, variables, rotation, and invulnerability.

Not every definition field needs to support instance overrides.

---

# Definition vs Instance Visual Indicator

The distinction should be obvious.

```text
◆ Definition Value
● Instance Override
```

Example:

```text
Dialogue
mypack:gate_guard

● Overridden for this entity

[Reset to Definition]
```

Creators should never be unsure whether they are editing one NPC or every NPC using the definition.

---

# In-World Entity Tool

A dedicated creator wand or editor tool would make placement much faster.

```text
Right Click Block
→ Place Custom Entity

Right Click Custom Entity
→ Edit Entity Instance

Shift + Right Click Entity
→ Quick Actions
```

Quick actions can include:

- Edit
- Duplicate
- Move
- Rotate
- Delete
- Copy ID
- Edit Definition

---

# Placement Mode

```text
Selected:
Village Guard

Left Click
Place

Mouse Wheel
Rotate

R
Rotate 90°

Shift + Mouse Wheel
Change selected entity

Escape
Exit
```

A transparent preview should show the entity before placement.

The preview should reflect rotation, hitbox, scale, and ground position.

---

# Live Testing

The editor should make iteration fast.

```text
[Spawn Test Entity]
[Reset Test]
[Delete Test]
```

Creators should be able to test AI, combat, animation, dialogue, shops, factions, drops, rules, and custom attacks without placing permanent entities.

---

# AI Debugger

Complex AI needs good debugging tools.

```text
Entity Debug

Definition
mypack:village_guard

Health
24 / 30

Target
Zombie #458

Current Goal
Melee Attack

Navigation
Moving → 130, 65, -21

Faction
Village

Variables
angry = false
phase = 1

Rules
3 active
```

Useful debug information includes current target, AI goal, navigation target, home, patrol point, faction, variables, rules, cooldowns, active attack, and animation state.

---

# Rule Debugger

The rule debugger should explain why a rule failed.

```text
Phase Two

Trigger
✓ Health Changed

Conditions
✓ Health <= 50%
✗ phase == 1

Rule did not execute.
```

The same debugger can later be shared with stats, quests, dialogue, abilities, and zones.

---

# Validation

Entity definitions should be validated before saving.

Possible errors:

- Invalid resource ID
- Duplicate ID
- Missing texture
- Missing model
- Unknown model provider
- Unknown AI goal
- Missing faction
- Missing dialogue
- Missing shop
- Invalid loot table
- Invalid entity reference
- Invalid attribute value
- Invalid variable reference
- Broken rule
- Missing required component

Possible logical warnings:

- Melee combat but no target selector
- Boss bar enabled with very low health
- Natural spawning enabled but no spawn dimensions
- Patrol AI with no patrol
- Dialogue interaction with no dialogue selected
- Ranged attack with no projectile

Example:

```text
⚠ 2 Problems

ERROR
Texture does not exist:
mypack:textures/entity/bandti.png

WARNING
Entity has melee combat but no targeting goals.
```

---

# References

The editor should show where an entity is used.

```text
References to mypack:bandit

Quests
• clear_bandit_camp
• road_to_village

Spawn Tables
• forest_bandits

Dialogue
• prisoner_dialogue

Boss
• bandit_chief
```

Deleting referenced entities should show a warning before removal.

---

# Undo and Redo

Recommended shortcuts:

```text
Ctrl + Z    Undo
Ctrl + Y    Redo
```

Undo should cover property changes, component changes, AI reordering, equipment changes, patrol edits, rules, variables, nodes, and connections.

---

# Basic and Advanced Modes

## Basic Mode

Recommended sections:

```text
General
Appearance
Attributes
Movement
AI
Combat
Equipment
Interaction
Drops
```

A beginner should be able to build:

```text
Bandit
20 HP
Iron Sword
Attacks Players
Drops Bandit Loot
```

without seeing a node graph.

## Advanced Mode

Adds:

```text
Variables
Rules
Schedules
Advanced Spawn Conditions
Custom Attacks
Instance Overrides
Raw JSON
Runtime Debugging
```

The underlying JSON format remains identical.

---

# Recommended First Version

A strong first version should focus on the core workflow.

## Entity Browser

- List entities
- Search
- Create
- Duplicate
- Delete
- Validation state

## General

- Name
- ID
- Tags

## Appearance

- Built-in model
- Texture
- Scale
- Hitbox
- 3D preview

## Attributes

- Health
- Movement speed
- Attack damage
- Armor
- Follow range

## Movement

- Ground movement
- Basic movement options

## Equipment

- Main hand
- Off hand
- Armor

## AI

- Goal list
- Goal priority
- Wander
- Look at player
- Follow
- Guard position
- Melee attack

## Targeting

- Player
- Entity type
- Retaliation

## Combat

- Basic melee
- Basic ranged

## Interaction

- Dialogue
- Shop
- Generic action

## Drops

- Loot table
- XP

## Persistence

- Despawn
- Persistent entity

## World Editing

- Spawn tool
- Instance editor
- Move
- Rotate
- Delete

## Advanced

- JSON preview
- Validation

This already supports a large number of useful NPC and enemy types.

---

# Second Version

Later versions can add:

- Patrol editor
- Schedules
- Natural spawning editor
- Factions
- Variables
- Rule graphs
- Boss bar
- Boss phases
- Custom attacks
- Custom models
- Custom animations
- Conditional textures
- Companion behavior
- Unique NPCs
- Respawning NPCs
- Instance overrides
- Advanced debugging
- Reference migration

---

# Recommended Editor Architecture

```text
EntityBrowserScreen
    ↓
EntityEditorScreen
    ├── ComponentNavigation
    ├── PropertyEditor
    └── EntityPreview

Specialized Editors
    ├── EquipmentEditor
    ├── AiGoalEditor
    ├── PatrolEditor
    ├── ScheduleEditor
    ├── InteractionEditor
    └── SpawnEditor

Advanced Editors
    ├── RuleGraphEditor
    ├── CustomAttackEditor
    ├── JsonPreview
    └── RuntimeDebugger

World Tools
    ├── EntityPlacementTool
    ├── InstanceEditor
    ├── PatrolPlacementTool
    └── PositionSelector
```

This prevents one enormous GUI class from handling every feature.

---

# Consistency With RPG Framework

The editor should use the same design language as the other creator tools.

```text
LIST             OBJECT EDITOR             LOGIC
────             ─────────────             ─────

Stats       →    Property Panel       →    Node Graph
Entities    →    Component Editor     →    Node Graph
Classes     →    Property Panel       →    Node Graph
Quests      →    Property Panel       →    Node Graph
Dialogue    →    Property Panel       →    Node Graph
Zones       →    Property Panel       →    Node Graph
Abilities   →    Property Panel       →    Node Graph
```

Shared systems should include:

- Resource pickers
- Search
- Validation
- Condition editor
- Action editor
- Node graph
- Reference viewer
- JSON preview
- Undo / redo
- In-world position selectors

---

# Recommended Creator Workflow

```text
1. Open RPG Framework Editor
2. Open Custom Entities
3. Click New
4. Select Guard template
5. Name it Village Guard
6. Choose humanoid model
7. Select texture
8. Configure health and movement
9. Give it a sword
10. Add guard AI
11. Configure faction
12. Configure dialogue
13. Click Spawn Test Entity
14. Test behavior
15. Save
16. Enter Placement Mode
17. Place guards around the village
18. Configure individual patrols if needed
```

The generated definition becomes:

```text
data/mypack/rpg/entities/village_guard.json
```

Placed entities store only their per-instance state and overrides.

---

# Core Editor Principle

The editor should not look like:

```text
Entity
├── 187 configuration fields
```

It should look like:

```text
Village Blacksmith

Appearance
AI
Dialogue
Shop
Faction
```

with:

```text
+ Add Component
```

The recommended design is therefore:

```text
Component-Based Property Editor
            +
Live 3D Preview
            +
Specialized Visual Tools
            +
In-World Editing
            +
Node-Based Advanced Logic
            +
JSON as the Source of Truth
```

This keeps simple NPC creation fast while still allowing advanced creators to build complex RPG characters, enemies, companions, and bosses without writing Java code.
