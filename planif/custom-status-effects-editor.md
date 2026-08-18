# Custom Status Effects In-Game Editor

## Overview

The **Custom Status Effects In-Game Editor** allows modpack creators to create, modify, test, and debug custom status effects without manually editing JSON files.

The editor works directly with the same effect definitions used by the runtime.

```text
JSON File
    ↕
Effect Definition
    ↕
In-Game Editor
```

The JSON remains the source of truth.

The editor should make simple effects very fast to create while still supporting complex RPG mechanics.

Examples:

- Bleeding
- Burning
- Poison
- Frozen
- Wet
- Stunned
- Rooted
- Silenced
- Berserk
- Blessed
- Cursed
- Marked
- Vulnerable
- Regeneration
- Mana Burn
- Corruption
- Infection
- Hidden internal effects

The recommended editor structure is:

```text
EFFECT LIST
    ↓
PROPERTY EDITOR
    ↓
LIVE EFFECT SIMULATOR
    ↓
RULE / EVENT GRAPH
```

Simple effects should be configurable almost entirely through forms.

Advanced creators can use rules, events, interactions, conditions, and debugging tools.

---

# Design Goals

The editor should be:

- Easy to understand
- Consistent with the other RPG Framework editors
- Fast for simple effects
- Powerful for advanced effects
- Compatible with manual JSON editing
- Safe against invalid configuration
- Highly visual
- Good for testing runtime behavior
- Integrated with Custom Stats
- Integrated with Custom Entities
- Integrated with Abilities, Items, Quests, Zones, and Dialogue
- Extensible through registries

The most important UX principle is:

> The creator should only need advanced tools when the effect itself is advanced.

Creating a simple speed buff should not require opening a node graph.

Creating a complex stacking frost effect should still be possible.

---

# Main Effects Screen

The first screen lists all custom status effects.

Example:

```text
┌──────────────────────────────────────────────────────────────┐
│ Custom Status Effects                              [+ New] │
├──────────────────────────────────────────────────────────────┤
│ Search [____________________]     Category [All ▼]           │
│                                                              │
│ 🩸 Bleeding                                                 │
│    mypack:bleeding             Harmful • Stackable          │
│                                                              │
│ ❄ Frozen                                                    │
│    mypack:frozen               Harmful • Crowd Control      │
│                                                              │
│ ✦ Blessed                                                   │
│    mypack:blessed              Beneficial                   │
│                                                              │
│ ◉ Marked                                                    │
│    mypack:marked               Neutral                      │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

Each entry may show:

- Icon
- Display name
- Resource ID
- Category
- Important tags
- Stackable indicator
- Infinite duration indicator
- Validation warning
- Validation error
- Unsaved state

Possible filters:

- All
- Beneficial
- Harmful
- Neutral
- Stackable
- Infinite
- Hidden
- Invalid
- Recently edited

Possible right-click actions:

```text
Edit
Duplicate
Rename
Copy ID
Apply To Self
Open JSON
Delete
```

---

# Creating an Effect

Pressing **New** opens a lightweight creation dialog.

Example:

```text
Create Status Effect

Name
[ Bleeding                     ]

ID
[ mypack:bleeding              ]

Template
[ Damage Over Time ▼           ]

Category
[ Harmful ▼                    ]

                 [Cancel] [Create]
```

The creator should not need to configure every detail before the effect exists.

---

# Effect Templates

Templates should provide useful starting configurations.

Recommended templates:

- Blank
- Buff
- Debuff
- Damage Over Time
- Healing Over Time
- Stacking Debuff
- Crowd Control
- Stat Modifier
- Attribute Modifier
- Permanent Effect
- Hidden Effect

Templates should not create different runtime effect types.

They only generate initial settings.

For example, **Damage Over Time** could add:

```text
Harmful category
Timed duration
Visible HUD
Periodic rule
```

A **Crowd Control** template could add:

```text
Harmful category
Restriction component
Timed duration
Crowd Control tag
```

---

# Main Effect Editor

Recommended layout:

```text
┌─────────────────────────────────────────────────────────────────────┐
│ 🩸 Bleeding                                      [Test] [Save]    │
├──────────────┬──────────────────────────────┬───────────────────────┤
│ General      │                              │ EFFECT PREVIEW        │
│ Duration     │                              │                       │
│ Stacking     │      PROPERTY EDITOR         │ 🩸 Bleeding ×3       │
│ Modifiers    │                              │ Harmful               │
│ Restrictions │                              │ 00:08                 │
│ Rules        │                              │                       │
│ Events       │                              │ [Apply] [Remove]      │
│ Visuals      │                              │                       │
│ Display      │                              │ Stacks [-] 3 [+]      │
│ Advanced     │                              │ Level  [-] 1 [+]      │
└──────────────┴──────────────────────────────┴───────────────────────┘
```

The editor contains three major areas:

- Navigation
- Property editor
- Live effect simulator

The simulator should remain visible on most pages.

---

# General Page

Example:

```text
General

Name
[ Bleeding                            ]

ID
[ mypack:bleeding                     ]

Description
[ The target continuously loses       ]
[ health.                              ]

Icon
[ 🩸 ] [Select Texture]

Color
[ ■ ] #A32A2A

Category
[ Harmful ▼ ]
```

Possible properties:

- Name
- ID
- Description
- Icon
- Color
- Category

Changing an ID should show reference warnings if the effect is already used elsewhere.

---

# Category Editor

The primary category should use a simple dropdown.

```text
Category

[ Harmful ▼ ]
```

Available categories:

```text
Beneficial
Harmful
Neutral
```

The editor can use different visual treatments for each category, but category meaning should never rely only on color.

---

# Tags Editor

Tags should be easy to view and edit.

Example:

```text
Tags

[ physical × ]
[ damage_over_time × ]
[ bleed × ]

                         [+ Add Tag]
```

Pressing **Add Tag** opens:

```text
Select Tag

Search
[ bleed________________ ]

Available
────────────────────────
rpg:bleed
rpg:physical
rpg:damage_over_time

Custom
────────────────────────
[ Create Tag ]
```

Creators should also be able to enter custom resource tags manually.

Example:

```text
mypack:blood_magic
```

Tags should support autocomplete from existing tags.

---

# Duration Editor

Example:

```text
Duration

Type
(o) Timed
( ) Infinite

Default Duration
[ 200 ] ticks

Maximum Duration
[ 600 ] ticks

[✓] Show duration to player
```

Recommended display unit toggle:

```text
Ticks | Seconds
```

For example:

```text
200 ticks
=
10 seconds
```

Internally the system can continue storing ticks.

---

# Duration Preview

The live simulator can show remaining duration.

Example:

```text
Duration Preview

0s ─────────────────────●──────── 30s
                        18s
```

Controls:

```text
[Start]
[Pause]
[Reset]
```

This helps test HUD behavior and expiration events.

---

# Infinite Effects

When Infinite is selected:

```text
Duration

Type
( ) Timed
(o) Infinite
```

Timed-only settings should disappear.

The preview should show:

```text
Duration
∞
```

If the creator enables a duration HUD on an infinite effect, the editor may show a warning.

---

# Level Editor

Effects can optionally support levels.

Example:

```text
Effect Level

[✓] Enable Levels

Minimum
[ 1 ]

Maximum
[ 5 ]

Default
[ 1 ]
```

Elsewhere in the editor, values can scale according to:

```text
Fixed
Per Level
Per Stack
Level × Stack
```

---

# Stacking Editor

Stacking needs a specialized UI because it strongly affects runtime behavior.

Example:

```text
Stacking

Mode
[ Stack ▼ ]

Maximum Stacks
[ 5 ]

On Reapply
[ Refresh Duration ▼ ]

At Maximum
[ Trigger Event ▼ ]

[✓] Show stack count
```

Available modes:

```text
Replace
Refresh
Extend
Stack
Independent Stacks
```

Only relevant fields should appear for the selected mode.

---

# Replace Mode

Example:

```text
Mode
[ Replace ]

New Application
[ Replace Existing Effect ]
```

Possible optional settings:

- Replace duration
- Replace level
- Replace source
- Reset runtime data

---

# Refresh Mode

Example:

```text
Mode
[ Refresh ]

On Reapply
[ Reset to Applied Duration ]
```

This mode keeps one instance while refreshing duration.

---

# Extend Mode

Example:

```text
Mode
[ Extend ]

Maximum Duration
[ 600 ticks ]
```

The new duration is added to the current duration.

---

# Stack Mode

Example:

```text
Mode
[ Stack ]

Maximum Stacks
[ 5 ]

On Reapply
[ Add Stack + Refresh Duration ▼ ]

At Maximum
[ Trigger Event ▼ ]
```

Possible maximum-stack behavior:

- Ignore
- Refresh duration
- Extend duration
- Trigger event
- Replace oldest stack

---

# Independent Stacks

Independent stacks should be considered an advanced option.

Example runtime state:

```text
Bleeding

Stack 1    4.2s
Stack 2    7.8s
Stack 3    9.1s
```

Each stack expires separately.

This may be added after the initial editor version.

---

# Stack Simulator

The live preview should allow manual stack testing.

Example:

```text
Stack Test

🩸 Bleeding ×3

[- Stack] [+ Stack]

Current:
Stacks:   3 / 5
Duration: 8.4s
Level:    1

[Apply Again]
```

When **Apply Again** is pressed, the editor should explain what happened.

Example:

```text
Before:
3 stacks
6 seconds

After:
4 stacks
10 seconds

Reason:
Stack added
Duration refreshed
```

This makes stacking behavior much easier to understand.

---

# Modifier Editor

The Modifiers page contains attribute modifiers.

Example:

```text
Modifiers

Movement Speed
-5% per stack                         [Edit] [X]

Armor
-2 per level                          [Edit] [X]

Attack Damage
+10% fixed                            [Edit] [X]

                                  [+ Modifier]
```

Pressing **Add Modifier** opens:

```text
Add Modifier

Attribute
[ Movement Speed ▼ ]

Operation
[ Multiply Total ▼ ]

Scaling
[ Per Stack ▼ ]

Value
[ -0.05 ]

                         [Cancel] [Add]
```

Possible scaling modes:

- Fixed
- Per Level
- Per Stack
- Level × Stack

---

# Modifier Preview

The simulator should calculate final modifier values.

Example:

```text
Frozen

Stacks
4

Calculated Modifiers

Movement Speed
Base: -5% per stack

Final:
-20%
```

If level and stacks are both involved:

```text
Base
-2%

Level
3

Stacks
4

Final
-24%
```

Creators should not have to calculate formulas manually when testing an effect.

---

# Restrictions Editor

Restrictions allow creators to make crowd-control effects.

Example:

```text
Restrictions

While this effect is active:

[✓] Can Move
[✓] Can Jump
[✓] Can Sprint
[✓] Can Attack
[✓] Can Use Items
[✓] Can Use Abilities
[✓] Can Interact
[✓] Can Target
```

For a Stunned effect:

```text
[ ] Can Move
[ ] Can Jump
[ ] Can Attack
[ ] Can Use Items
[ ] Can Use Abilities
[✓] Can Look Around
```

The UI should use positive wording such as **Can Move** rather than inverted settings such as `disable_movement`.

---

# Restriction Presets

The editor can offer convenience presets.

Example:

```text
Preset
[ Stun ▼ ]
```

Suggested presets:

- None
- Stun
- Root
- Silence
- Disarm
- Custom

These are editor shortcuts only.

They should not create special hardcoded runtime effect types.

---

# Rules Page

Rules should first be displayed as readable summaries.

Example:

```text
Rules

Damage Tick
Every 40 ticks
→ Deal damage equal to stack count

Mana Drain
Every 20 ticks
→ Mana -5

Wet Interaction
When Applied
IF target has Wet
→ Remove Wet
→ Remove Burning

                                  [+ Rule]
```

Clicking a rule opens the graph editor.

---

# Rule Graph

The rule graph should reuse the shared RPG Framework node editor.

Example for Bleeding:

```text
┌─────────────────┐
│ Every 40 Ticks  │
└────────●────────┘
         │
         ▼
┌─────────────────┐
│ Effect Stacks   │
└────────●────────┘
         │
         ▼
┌─────────────────┐
│ Damage Target   │
│ Amount = input  │
└─────────────────┘
```

Example for Mana Burn:

```text
[Every 20 Ticks]
        │
        ▼
[Target Mana > 0]
        │
        ▼
[Modify Mana -5]
```

Node categories should match other framework editors:

```text
Trigger
Condition
Action
Value
Flow
```

---

# Events Editor

Lifecycle events should have a dedicated editor.

Example:

```text
Events

On Applied                         [Edit]
On Reapplied                       [Edit]
On Removed                         [Edit]
On Expired                         [Edit]
On Stack Added                     [Edit]
On Stack Removed                   [Edit]
On Maximum Stacks                  [Edit]
On Level Changed                   [Edit]
```

Simple events should use an action list.

A node graph should only be required for advanced logic.

---

# Simple Event Editor

Example:

```text
On Maximum Stacks

Actions
─────────────────────────────

1. Apply Effect
   mypack:stunned
   Duration: 40 ticks

2. Remove Effect
   mypack:frozen

3. Play Sound
   minecraft:block.glass.break

                         [+ Action]

[Open Advanced Graph]
```

This keeps common event logic fast to configure.

---

# Removal Conditions Editor

Example:

```text
Removal

Normal Removal
[✓] Duration expires

Additional Conditions
────────────────────────────────

Target enters water
→ Remove

Target dies
→ Remove

                              [+ Condition]
```

Another example:

```text
Remove When

[ Stat: Corruption ] [ < ] [ 20 ]
```

Possible removal behavior:

- Remove entire effect
- Remove one stack
- Reduce duration
- Set duration

---

# Effect Interaction Editor

Effect interactions can use the generic rule system internally, but a specialized editor can make common interactions easier.

Example:

```text
Interactions

When this effect is applied:

IF target has
[ Wet ]

Then:
Remove Wet
Remove Burning
Spawn Steam

                               [+ Interaction]
```

Possible interactions:

- Wet + Burning
- Frozen + Wet
- Poison + Fire
- Oil + Fire
- Curse + Blessing

The specialized UI should generate normal triggers, conditions, and actions underneath.

---

# Visuals Editor

Example:

```text
Visuals

Particles
[✓]

Particle
[ minecraft:snowflake ▼ ]

Interval
[ 10 ticks ]

Count
[ 2 ]


Entity Tint
[✓]

Color
[ ■ ] #87D9FF


Glow
[ ]

Screen Overlay
[ ]

Looping Sound
[ ]
```

Only enabled visual components should show their detailed settings.

Possible visual types:

- Particles
- Entity tint
- Glow
- Screen overlay
- HUD overlay
- Looping sound
- Custom addon visual provider

---

# Visual Preview

The preview should be able to render the effect on an entity model.

Example:

```text
┌───────────────────────────┐
│                           │
│       PLAYER MODEL        │
│                           │
│      ❄   ❄                │
│         🧍                │
│    ❄         ❄            │
│                           │
│     Frozen ×4             │
└───────────────────────────┘
```

Preview target:

```text
Preview Target
[ Player ▼ ]
```

Possible choices:

- Player
- Zombie
- Skeleton
- Villager
- Custom Entity

The creator should be able to preview tint, particles, glow, and equipment interaction.

---

# Display Editor

Example:

```text
Display

[✓] Visible To Player

Show In
[ HUD + Inventory ▼ ]

[✓] Show Icon
[✓] Show Name
[✓] Show Duration
[✓] Show Stacks
[ ] Show Level

HUD Priority
[ 10 ]

Preview
─────────────────────────────

🩸 Bleeding ×3     00:08
```

Possible visibility modes:

- HUD + Inventory
- HUD Only
- Inventory Only
- Hidden
- Debug Only

---

# Hidden Effects

Hidden effects are useful for internal gameplay state.

Examples:

```text
mypack:recently_dodged
mypack:boss_phase_transition
mypack:ability_lock
```

When an effect is hidden, the creator should still see it normally inside the editor and debugger.

---

# Persistence Editor

Persistence settings can live under Advanced.

Example:

```text
Persistence

On Death
[ Remove ▼ ]

On Logout
[ Keep ▼ ]

On Dimension Change
[ Keep ▼ ]

When Source Disappears
[ Keep ▼ ]
```

Possible values:

```text
Keep
Remove
Use Default
```

---

# Effect Simulator

The effect simulator should be one of the most important parts of the editor.

Example:

```text
Effect Simulator
────────────────────────────────────

Target
[ Current Player ▼ ]

Effect
Bleeding

Duration
[ 200 ]

Level
[ 1 ]

Stacks
[ 3 ]

Source
[ None ▼ ]

[Apply Effect]
[Remove Effect]

────────────────────────────────────

Runtime

Remaining
146 ticks

Stacks
3

Level
1

Next Periodic Trigger
14 ticks

Active Modifiers
Movement Speed: -15%

Restrictions
None

Rules Executed
Damage Tick ×2
```

This greatly reduces iteration time.

---

# Preview Simulation

Preview mode does not modify the real game world.

Useful for testing:

- HUD
- Duration
- Stacks
- Levels
- Modifier calculations
- Visuals
- Category
- Sorting

---

# Runtime Application Test

Runtime mode applies the actual effect to an entity.

Useful for testing:

- Damage
- Healing
- Movement restrictions
- Ability restrictions
- Rules
- Custom Stats interaction
- Entity AI reactions
- Immunities
- Source tracking

The mode should be clearly visible so creators know whether they are simulating or affecting the world.

---

# Test Target Selection

Possible targets:

- Current Player
- Looked-At Entity
- Selected Custom Entity
- Spawn Test Dummy

If a target is immune, the test panel should explain why.

Example:

```text
Effect Application Failed

Reason:
Target is immune to tag:
rpg:bleed
```

---

# Immunity Testing

The simulator should display immunity and resistance results.

Example:

```text
Apply To
Skeleton

Result
────────────────────────

✗ Effect rejected

Reason:
Target is immune to:
#rpg:bleed
```

Resistance example:

```text
Effect Applied

Original Duration
200 ticks

Final Duration
100 ticks

Reason:
Poison Duration Resistance = 0.5
```

---

# Source Testing

The simulator should let creators specify effect source.

Example:

```text
Source

(o) None
( ) Current Player
( ) Selected Entity
( ) Ability
( ) Custom
```

This allows testing damage attribution, kill credit, source conditions, quest behavior, and faction behavior.

---

# Rule Debugger

Rules should explain why they did or did not execute.

Example:

```text
Rule: Mana Drain

Trigger
✓ Interval reached

Conditions
✓ Target has Mana
✗ Mana > 0

Actions not executed.
```

Successful interaction example:

```text
Wet Interaction

Trigger
✓ Burning applied

Condition
✓ Target has Wet

Actions
✓ Removed Wet
✓ Removed Burning
✓ Spawned Steam
```

The same debugging system should ideally be shared by Stats, Effects, Entities, Quests, Dialogue, Abilities, and Zones.

---

# Advanced Page

Example:

```text
Advanced

File
data/mypack/rpg/effects/bleeding.json

[View JSON]
[Reload From Disk]

References
23 references

[View References]

Validation
✓ No errors

Runtime
[Open Effect Debugger]
```

Possible advanced tools:

- JSON preview
- Raw JSON editor
- Reload
- Reference viewer
- Validation
- Runtime debugger
- Persistence
- Source behavior

---

# JSON Preview

The editor should expose the generated JSON.

Example:

```json
{
  "id": "mypack:bleeding",
  "category": "harmful",
  "stacking": {
    "mode": "stacks",
    "max_stacks": 5
  }
}
```

A read-only JSON preview is enough for the first version.

Raw JSON editing can be added later with syntax highlighting, parsing errors, validation, undo, and safe recovery.

---

# Reference Viewer

Effects may be referenced by many systems.

Example:

```text
References to mypack:bleeding

Abilities
• serrated_strike
• blood_slash

Items
• rusty_dagger

Entities
• cave_bandit

Quests
• wounded_soldier

Zones
• blood_temple
```

This is particularly useful before renaming or deleting an effect.

---

# Validation

Possible validation errors:

- Invalid ID
- Duplicate ID
- Missing icon
- Invalid category
- Invalid tag
- Default duration greater than maximum
- Invalid maximum stack count
- Unknown attribute
- Invalid modifier operation
- Missing referenced effect
- Broken event
- Broken rule
- Unknown action
- Unknown condition
- Invalid restriction
- Missing dependency

Logical warnings are also useful.

Examples:

```text
WARNING
Effect uses stack scaling but stacking is disabled.

WARNING
On Maximum Stacks exists but no maximum stack count is defined.

WARNING
Effect is infinite but Show Duration is enabled.

WARNING
Effect disables abilities but no ability system is available.
```

Warnings should generally not prevent saving.

Errors should prevent saving when the generated effect would be invalid.

---

# Unsaved Changes

Modified effects should show an indicator.

Example:

```text
Bleeding *
```

Closing should show:

```text
You have unsaved changes.

[Discard]
[Cancel]
[Save]
```

---

# Undo and Redo

Recommended shortcuts:

```text
Ctrl + Z    Undo
Ctrl + Y    Redo
```

Undoable operations should include:

- Property changes
- Tag changes
- Stack configuration
- Modifier changes
- Restriction changes
- Rule changes
- Event actions
- Node creation
- Node deletion
- Node connections
- Visual changes

---

# Basic and Advanced Modes

The editor can optionally expose two complexity levels.

## Basic Mode

Recommended pages:

```text
General
Duration
Stacking
Modifiers
Restrictions
Display
```

This is enough for effects such as:

```text
Speed Boost
Poison
Bleeding
Regeneration
Stunned
Slow
Berserk
```

## Advanced Mode

Adds:

```text
Rules
Events
Interactions
Removal Conditions
Visuals
Persistence
Source Behavior
Raw JSON
Debugging
```

The JSON format remains the same.

The mode only changes what the UI exposes.

---

# Example Workflow: Bleeding

```text
1. Open Custom Effects
2. Click New
3. Choose Damage Over Time
4. Name it Bleeding
5. Category → Harmful
6. Add tags:
   physical
   bleed
   damage_over_time
7. Duration → 10 seconds
8. Stacking → Stack
9. Maximum stacks → 5
10. Refresh duration when reapplied
11. Add periodic rule:
    Every 40 ticks
    Damage = Stack Count
12. Show stacks on HUD
13. Test at 1 stack
14. Test at 5 stacks
15. Save
```

Most of this should be possible without opening the graph editor.

---

# Example Workflow: Frozen

```text
Frozen

Category
Harmful

Tags
Ice
Crowd Control
Movement Impairing

Duration
5 seconds

Stacking
Maximum 5

Modifier
-5% Movement Speed per stack

At 5 stacks
→ Apply Stunned
→ Remove Frozen

Visuals
Snow particles
Blue tint
```

---

# Example Workflow: Stunned

```text
1. New Effect
2. Choose Crowd Control template
3. Name → Stunned
4. Category → Harmful
5. Duration → 2 seconds
6. Restrictions:
   Can Move = false
   Can Jump = false
   Can Attack = false
   Can Use Abilities = false
   Can Use Items = false
7. Show icon and duration
8. Apply to self
9. Test
10. Save
```

No rule graph is required.

---

# Recommended First Version

A strong first editor version should include:

## Effect Browser

- List effects
- Search
- Category filters
- New
- Duplicate
- Delete
- Validation state

## General

- Name
- ID
- Description
- Icon
- Color
- Category
- Tags

## Duration

- Timed
- Infinite
- Default duration
- Maximum duration
- Ticks / seconds display

## Level

- Enable level
- Minimum
- Maximum
- Default

## Stacking

- Replace
- Refresh
- Extend
- Stack
- Maximum stacks
- Reapply behavior

## Modifiers

- Attribute
- Operation
- Value
- Stack scaling
- Level scaling

## Restrictions

- Movement
- Jumping
- Attack
- Items
- Abilities

## Events

- On Applied
- On Removed
- On Expired
- On Stack Added
- On Maximum Stacks
- Action list

## Rules

- Shared rule graph
- Interval trigger
- Shared conditions
- Shared actions

## Display

- Icon
- Name
- Duration
- Level
- Stack count
- Hidden

## Testing

- Preview duration
- Preview stacks
- Preview level
- Apply to self
- Remove from self
- Calculated modifiers

## Advanced

- JSON preview
- Validation
- Reference viewer

This already supports most common RPG status effects.

---

# Later Versions

Future versions can add:

- Independent stack simulation
- Effect interaction helper
- Advanced visuals
- Screen overlays
- Source simulation
- Resistance testing
- Timeline debugger
- Full runtime debugger
- Custom render providers
- Effect parameters
- Raw JSON editing
- Advanced persistence
- Addon editor widgets

---

# Recommended Editor Architecture

```text
EffectBrowserScreen
    ↓
EffectEditorScreen
    ├── EffectNavigation
    ├── PropertyEditor
    └── EffectSimulator

Specialized Editors
    ├── TagEditor
    ├── DurationEditor
    ├── StackingEditor
    ├── ModifierEditor
    ├── RestrictionEditor
    ├── EventEditor
    └── VisualEditor

Advanced Editors
    ├── RuleGraphEditor
    ├── InteractionEditor
    ├── JsonPreview
    ├── ReferenceViewer
    └── EffectDebugger
```

Reusable framework widgets should include:

- Resource pickers
- Tag picker
- Condition editor
- Action editor
- Node graph
- Reference viewer
- Validation panel
- JSON preview
- Undo / redo
- Search

---

# Consistency With RPG Framework

All creator tools should share the same basic interaction language.

```text
LIST             OBJECT EDITOR             LOGIC
────             ─────────────             ─────

Stats       →    Property Editor      →    Node Graph
Effects     →    Property Editor      →    Node Graph
Entities    →    Component Editor     →    Node Graph
Quests      →    Property Editor      →    Node Graph
Dialogue    →    Property Editor      →    Node Graph
Abilities   →    Property Editor      →    Node Graph
Zones       →    Property Editor      →    Node Graph
```

The Status Effect editor should feel like another part of the same creator application.

---

# Core Editor Principle

The editor should make simple effects simple.

Creating:

```text
Speed Boost
+20% Movement Speed
10 seconds
```

should require only a few fields.

Creating:

```text
Frozen

Stacks up to 5
-5% speed per stack
Refreshes duration
At 5 stacks:
→ Stun target
→ Remove Frozen
Blue tint
Snow particles
```

should also be possible without writing Java.

The recommended design is:

```text
Property Panels
      +
Live Effect Simulator
      +
Simple Event Lists
      +
Specialized Effect Editors
      +
Node-Based Advanced Logic
      +
JSON as the Source of Truth
```

The strongest specialized feature should be the **Live Effect Simulator**.

Stats benefit from value previews.

Entities benefit from 3D previews.

Status effects naturally benefit from being able to manipulate:

```text
Duration
Stacks
Level
Target
Source
```

and immediately see the resulting behavior.
