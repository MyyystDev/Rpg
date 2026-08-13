# Custom Status Effects System

## Overview

The **Custom Status Effects System** lets modpack creators define fully configurable status effects through JSON.

It is inspired by Minecraft potion effects, but is designed as a more general RPG system. Effects can represent:

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
- Internal gameplay states

Each effect is stored in its own file:

```text
data/<namespace>/rpg/effects/bleeding.json
data/<namespace>/rpg/effects/frozen.json
data/<namespace>/rpg/effects/berserk.json
```

The framework should not hardcode what an effect means. Instead, each effect is built from reusable systems:

```text
Custom Effect
├── Identity
├── Category
├── Tags
├── Duration
├── Level
├── Stacking
├── Attribute Modifiers
├── Restrictions
├── Rules
├── Events
├── Removal Conditions
├── Visuals
└── Display
```

---

# Design Goals

The system should be:

- Data-driven
- Compatible with JSON and an in-game editor
- Generic and reusable
- Easy for simple effects
- Powerful for advanced RPG mechanics
- Usable on players and custom entities
- Integrated with Custom Stats
- Integrated with Custom Entities
- Integrated with Abilities, Items, Quests, Zones and Dialogue
- Extensible through registries

Core principle:

> A status effect is a temporary configurable behavior package attached to an entity.

---

# Identity

Every effect has a unique resource ID.

```json
{
  "id": "mypack:bleeding",
  "display": {
    "name": "Bleeding",
    "description": "The target continuously loses health.",
    "icon": "mypack:textures/effect/bleeding.png",
    "color": "#A32A2A"
  }
}
```

Possible identity properties:

- ID
- Name
- Description
- Icon
- Color

The ID is used by every other RPG system.

---

# Effect Category

Every effect should have one primary category:

```text
beneficial
harmful
neutral
```

Example:

```json
{
  "category": "harmful"
}
```

Examples:

```text
Bleeding       → Harmful
Poison         → Harmful
Frozen         → Harmful

Regeneration   → Beneficial
Blessed        → Beneficial
Berserk        → Beneficial

Wet            → Neutral
Marked         → Neutral
Internal Flag  → Neutral
```

Categories can be used by:

- HUD grouping
- Cleansing
- Filters
- Conditions
- Abilities
- Items
- AI

Example:

```text
Remove all Harmful effects
```

---

# Effect Tags

Effects should also support arbitrary tags.

```json
{
  "tags": [
    "rpg:physical",
    "rpg:damage_over_time",
    "rpg:bleed"
  ]
}
```

Possible tags:

```text
rpg:physical
rpg:magic
rpg:curse
rpg:disease
rpg:fire
rpg:ice
rpg:poison
rpg:bleed
rpg:damage_over_time
rpg:movement_impairing
rpg:crowd_control
```

Creators should be able to define their own tags.

Tags enable generic logic such as:

```text
Remove all effects tagged rpg:curse
```

```text
Entity is immune to all effects tagged rpg:disease
```

```text
Deal +25% damage to targets affected by rpg:ice
```

The combination of **Category + Tags** provides simple organization without limiting creators.

---

# Duration

Effects can be timed or infinite.

```json
{
  "duration": {
    "default": 200,
    "maximum": 600
  }
}
```

Possible properties:

- Default duration
- Maximum duration
- Infinite duration
- Duration visibility
- Duration persistence

Infinite example:

```json
{
  "duration": {
    "type": "infinite"
  }
}
```

Useful for:

- Permanent curses
- Quest states
- Equipment effects
- Zone states
- Hidden internal effects

---

# Effect Level

Effects can optionally support a level or amplifier.

```text
Poison I
Poison II
Poison III
```

Level represents the strength of one effect instance.

Example:

```text
Regeneration I  → Heal 1
Regeneration II → Heal 2
Regeneration III → Heal 3
```

Creators should be able to disable levels for effects that do not need them.

---

# Effect Stacks

Stacks represent repeated applications.

```text
Bleeding ×1
Bleeding ×2
Bleeding ×3
```

Level and stacks are separate concepts:

```text
Level  → Strength tier
Stacks → Accumulated applications
```

An effect could technically support both:

```text
Bleeding II ×3
```

---

# Stacking Modes

## Replace

The new application replaces the previous effect.

```text
Poison 20s
+ Poison 10s
= Poison 10s
```

## Refresh

The new application refreshes duration.

```text
Poison 6s
+ Poison 20s
= Poison 20s
```

## Extend

The duration is added.

```text
Poison 10s
+ Poison 20s
= Poison 30s
```

## Stack

Repeated applications increase stack count.

```json
{
  "stacking": {
    "mode": "stacks",
    "max_stacks": 5,
    "refresh_duration": true
  }
}
```

Example:

```text
Bleeding ×1
→ Bleeding ×2
→ Bleeding ×3
→ Bleeding ×4
→ Bleeding ×5
```

At maximum stacks, creators can choose behavior such as:

- Ignore application
- Refresh duration
- Extend duration
- Trigger an event
- Replace oldest stack

## Independent Stacks

Advanced future option:

```text
Bleeding
├── Stack 1: 6 seconds
├── Stack 2: 11 seconds
└── Stack 3: 15 seconds
```

Each stack expires independently.

---

# Attribute Modifiers

Effects can modify Minecraft attributes.

```json
{
  "attributes": [
    {
      "attribute": "minecraft:movement_speed",
      "operation": "multiply_total",
      "value": -0.3
    }
  ]
}
```

Examples:

```text
Frozen
→ -30% Movement Speed

Berserk
→ +30% Attack Damage
→ -20% Armor

Blessed
→ +4 Max Health
```

Modifiers should optionally scale with:

- Level
- Stack count
- Both

Example:

```json
{
  "attribute": "minecraft:movement_speed",
  "operation": "multiply_total",
  "value_per_stack": -0.05
}
```

Result:

```text
Frozen ×1 → -5%
Frozen ×2 → -10%
Frozen ×3 → -15%
Frozen ×4 → -20%
```

---

# Periodic Behavior

Effects should reuse the generic rule system.

```text
Trigger
   ↓
Conditions
   ↓
Actions
```

Example:

```json
{
  "rules": [
    {
      "trigger": {
        "type": "rpg:interval",
        "ticks": 40
      },
      "actions": [
        {
          "type": "rpg:damage",
          "amount": 2
        }
      ]
    }
  ]
}
```

This supports:

- Damage over time
- Healing over time
- Mana drain
- Corruption increase
- Radiation buildup
- Periodic particles
- Periodic sounds
- Summoning
- Custom stat changes

---

# Effect Events

Recommended lifecycle events:

- On Applied
- On Reapplied
- On Removed
- On Expired
- On Level Changed
- On Stack Added
- On Stack Removed
- On Maximum Stacks
- On Tick
- On Target Death

Example:

```text
Frozen reaches 5 stacks
        ↓
Apply Stunned
        ↓
Remove Frozen
        ↓
Play ice break sound
```

Manual removal and natural expiration should be separate events.

That allows mechanics such as:

```text
On Expired
→ Explode
```

without triggering when the effect is cleansed.

---

# Removal Conditions

Effects should support removal conditions in addition to duration.

Examples:

```text
Remove when target enters water
Remove when target reaches full health
Remove when target leaves a region
Remove when another effect is applied
Remove when a stat drops below 20
Remove after target attacks 5 times
```

Example:

```text
Burning

Remove when:
Target is in water
```

Removal conditions should reuse the shared condition system.

---

# Effect Interactions

Effects can interact using normal events, conditions and actions.

Example:

```text
Wet + Burning
→ Remove Wet
→ Remove Burning
→ Spawn Steam
```

```text
Wet + Frozen
→ Apply additional Frozen stacks
```

```text
Oiled + Burning
→ Add 3 Burning stacks
```

A dedicated hardcoded interaction system is not required.

Example rule:

```text
On Applied: Burning

IF target has Wet
→ Remove Wet
→ Remove Burning
→ Spawn Steam
```

---

# Restrictions

Effects should be able to restrict entity behavior.

Possible restrictions:

```text
Can Move
Can Jump
Can Attack
Can Use Abilities
Can Use Items
Can Interact
Can Sprint
Can Fly
Can Target
Can Rotate
```

This allows creators to build crowd-control effects without dedicated Java classes.

## Stunned

```text
Can Move = false
Can Jump = false
Can Attack = false
Can Use Abilities = false
Can Use Items = false
```

## Rooted

```text
Can Move = false
Can Attack = true
Can Use Abilities = true
```

## Silenced

```text
Can Move = true
Can Attack = true
Can Use Abilities = false
```

---

# Effect Immunities

Custom entities should be able to define effect immunities.

Specific effect:

```text
mypack:bleeding
```

Tag immunity:

```text
#rpg:disease
```

Example:

```json
{
  "effect_immunities": [
    "mypack:bleeding",
    "#rpg:poison"
  ]
}
```

Examples:

```text
Skeleton
→ Immune to Bleeding
→ Immune to Poison

Fire Elemental
→ Immune to Fire effects
```

---

# Effect Resistance

Advanced entity configuration can support partial resistance.

Possible resistance values:

- Application chance
- Duration multiplier
- Intensity multiplier
- Stack gain multiplier

Example:

```text
Poison Resistance

Application Chance: 50%
Duration Multiplier: 0.5
Intensity Multiplier: 0.75
```

This can be introduced after the core system.

---

# Source Tracking

Effect instances should track who or what applied them.

Example:

```text
Effect:
Bleeding

Target:
Bandit

Source:
Player
```

Possible sources:

- Player
- Entity
- Ability
- Item
- Zone
- Environment
- Command
- Unknown

Source tracking is important for:

- Kill attribution
- XP rewards
- Threat
- Faction behavior
- Quest objectives
- Damage ownership

Periodic damage should preserve the original source whenever possible.

---

# Effect Parameters

Advanced effects may carry runtime parameters.

Example:

```text
Burning

power = 4
source = player
```

Another example:

```text
Marked

owner = player_uuid
bonus_damage = 0.25
```

This can reduce the need to create multiple nearly identical effects.

Instead of:

```text
weak_burning
medium_burning
strong_burning
```

the creator can define:

```text
mypack:burning
```

and vary its level or parameters.

---

# Effect Instance

The definition and runtime instance should be separate.

Definition:

```text
mypack:bleeding
```

Runtime instance:

```text
Target:
Player

Duration:
83 ticks

Level:
1

Stacks:
3

Source:
Bandit #71
```

Possible architecture:

```text
EffectInstance
├── definitionId
├── target
├── source
├── remainingDuration
├── level
├── stacks
└── runtimeData
```

---

# Conditions

The effect system should expose generic conditions.

Recommended conditions:

- Has Effect
- Does Not Have Effect
- Effect Level
- Effect Stack Count
- Effect Remaining Duration
- Has Effect Category
- Has Effect Tag

Examples:

```text
Has Effect: mypack:frozen
```

```text
Bleeding Stacks >= 3
```

```text
Has any effect tagged rpg:curse
```

These conditions should work in:

- Abilities
- Custom Entities
- Quests
- Dialogue
- Items
- Zones
- AI
- Shops
- Rules
- Bosses

---

# Actions

Recommended generic actions:

- Apply Effect
- Remove Effect
- Remove Effect Category
- Remove Effect Tag
- Clear Effects
- Change Effect Duration
- Change Effect Level
- Add Effect Stack
- Remove Effect Stack
- Set Effect Stacks

Example:

```text
Apply Effect

Effect: mypack:bleeding
Duration: 200
Level: 1
Stacks: 1
```

---

# Cleansing

Categories and tags make cleansing generic.

Examples:

```text
Cleanse
→ Remove all Harmful effects
```

```text
Purify
→ Remove all effects tagged rpg:curse
```

```text
Bandage
→ Remove all effects tagged rpg:bleed
```

```text
Antidote
→ Remove all effects tagged rpg:poison
```

---

# Custom Stats Integration

Effects should directly use the Custom Stats system.

Examples:

```text
Mana Burn
Every second:
Mana -5
```

```text
Corrupted
Every second:
Corruption +1
```

```text
Calm
Sanity regeneration +50%
```

```text
Exhausted
Maximum Stamina -30%
```

The effect system should not duplicate resource mechanics that already exist in Custom Stats.

---

# Custom Entities Integration

Custom entities should be able to:

- Apply effects
- Resist effects
- Be immune to effects
- Query effects in AI
- React when effects are applied
- React when effects expire

Example:

```text
Skeleton

Immunities:
#rpg:bleed
#rpg:poison
```

Example AI:

```text
IF target has Frozen
→ Use Shatter Attack
```

---

# Ability Integration

Abilities should apply, consume and query effects.

Example:

```text
Fireball
→ Apply Burning for 5 seconds
```

Example:

```text
Shatter

Condition:
Target has Frozen

Actions:
Deal bonus damage
Remove Frozen
```

Example:

```text
Execute

Condition:
Target has Bleeding ×5

Actions:
Deal bonus damage
Remove Bleeding
```

This allows creator-made combat combos.

---

# Item Integration

Examples:

```text
Poison Sword
→ Apply Poison on hit
```

```text
Bandage
→ Remove effects tagged rpg:bleed
```

```text
Holy Water
→ Remove effects tagged rpg:curse
```

```text
Frost Bomb
→ Apply Frozen ×2
```

---

# Quest Integration

Quests should be able to query and modify effects.

Examples:

```text
Objective:
Become infected with Plague
```

```text
Objective:
Remove Curse of the Hollow King
```

```text
Reward:
Remove Corrupted
```

---

# Zone Integration

Zones should be able to apply or remove effects.

Examples:

```text
Poison Swamp
→ Apply Poison
```

```text
Frozen Mountain
→ Apply Cold
```

```text
Holy Sanctuary
→ Remove Curse effects
```

```text
Corrupted Area
→ Apply Corrupted
```

---

# Visuals

Effects can optionally modify presentation.

Possible visual features:

- Particles
- Entity tint
- Glow
- Screen overlay
- HUD overlay
- Looping sound
- Custom render hooks

Example:

```text
Frozen
→ Blue tint
→ Snow particles
```

Example:

```text
Corrupted
→ Purple particles
→ Screen vignette
```

Advanced visual systems can come after the core runtime is stable.

---

# HUD Display

Effects should optionally appear on the HUD.

Example:

```text
☠ Poison        0:23
🩸 Bleeding ×3  0:08
❄ Frozen ×4     0:12
```

Creators should control:

- Show icon
- Show name
- Show duration
- Show level
- Show stack count
- Hidden status

Possible visibility modes:

```text
Always
HUD Only
Inventory Only
Hidden
Debug Only
```

Hidden effects are useful for internal mechanics.

---

# Persistence

Possible persistence options:

- Keep on death
- Remove on death
- Keep after relog
- Remove on logout
- Keep across dimension changes
- Remove when leaving dungeon
- Remove when source disappears

Example:

```json
{
  "persistence": {
    "keep_on_death": false,
    "keep_on_logout": true
  }
}
```

---

# Example: Bleeding

```json
{
  "id": "mypack:bleeding",

  "display": {
    "name": "Bleeding",
    "icon": "mypack:textures/effect/bleeding.png",
    "color": "#A32A2A"
  },

  "category": "harmful",

  "tags": [
    "rpg:physical",
    "rpg:damage_over_time",
    "rpg:bleed"
  ],

  "duration": {
    "default": 200,
    "maximum": 600
  },

  "stacking": {
    "mode": "stacks",
    "max_stacks": 5,
    "refresh_duration": true
  },

  "rules": [
    {
      "trigger": {
        "type": "rpg:interval",
        "ticks": 40
      },

      "actions": [
        {
          "type": "rpg:damage",
          "amount": {
            "type": "rpg:effect_stacks",
            "multiplier": 1
          }
        }
      ]
    }
  ],

  "display_options": {
    "show_icon": true,
    "show_duration": true,
    "show_stacks": true
  }
}
```

Behavior:

```text
Bleeding ×1 → 1 damage every 40 ticks
Bleeding ×2 → 2 damage every 40 ticks
Bleeding ×3 → 3 damage every 40 ticks
...
Bleeding ×5 → 5 damage every 40 ticks
```

---

# Example: Frozen

```json
{
  "id": "mypack:frozen",

  "display": {
    "name": "Frozen",
    "icon": "mypack:textures/effect/frozen.png",
    "color": "#87D9FF"
  },

  "category": "harmful",

  "tags": [
    "rpg:ice",
    "rpg:movement_impairing",
    "rpg:crowd_control"
  ],

  "duration": {
    "default": 100
  },

  "stacking": {
    "mode": "stacks",
    "max_stacks": 5,
    "refresh_duration": true
  },

  "attributes": [
    {
      "attribute": "minecraft:movement_speed",
      "operation": "multiply_total",
      "value_per_stack": -0.05
    }
  ],

  "events": {
    "on_max_stacks": [
      {
        "type": "rpg:apply_effect",
        "effect": "mypack:stunned",
        "duration": 40
      },
      {
        "type": "rpg:remove_effect",
        "effect": "mypack:frozen"
      }
    ]
  }
}
```

Behavior:

```text
Frozen ×1 → -5% movement
Frozen ×2 → -10%
Frozen ×3 → -15%
Frozen ×4 → -20%
Frozen ×5 → Apply Stunned, then remove Frozen
```

---

# Example: Stunned

```json
{
  "id": "mypack:stunned",

  "display": {
    "name": "Stunned",
    "icon": "mypack:textures/effect/stunned.png",
    "color": "#FFD45C"
  },

  "category": "harmful",

  "tags": [
    "rpg:crowd_control",
    "rpg:stun"
  ],

  "duration": {
    "default": 40
  },

  "stacking": {
    "mode": "refresh"
  },

  "restrictions": {
    "can_move": false,
    "can_jump": false,
    "can_attack": false,
    "can_use_abilities": false,
    "can_use_items": false
  }
}
```

---

# Example: Berserk

```json
{
  "id": "mypack:berserk",

  "display": {
    "name": "Berserk",
    "icon": "mypack:textures/effect/berserk.png",
    "color": "#D64545"
  },

  "category": "beneficial",

  "tags": [
    "rpg:physical",
    "rpg:offensive"
  ],

  "duration": {
    "default": 200
  },

  "attributes": [
    {
      "attribute": "minecraft:attack_damage",
      "operation": "multiply_total",
      "value": 0.30
    },
    {
      "attribute": "minecraft:armor",
      "operation": "multiply_total",
      "value": -0.20
    }
  ]
}
```

---

# Recommended Internal Architecture

```text
EffectDefinition
├── id
├── display
├── category
├── tags
├── durationConfig
├── stackingConfig
├── attributeModifiers
├── restrictions
├── rules
├── events
├── visuals
└── persistence

EffectInstance
├── definition
├── target
├── source
├── remainingDuration
├── level
├── stacks
└── runtimeData

EffectManager
├── applyEffect()
├── removeEffect()
├── tickEffects()
├── addStack()
├── removeStack()
├── updateDuration()
├── evaluateRules()
└── fireEvents()

EffectRegistry
└── loaded effect definitions
```

Definitions are loaded from data packs.

Instances exist on living entities.

Other systems should interact through the Effect Manager rather than modifying runtime state directly.

---

# Extensibility

Possible registries:

```text
EffectVisualRegistry
EffectRestrictionRegistry
EffectStackingModeRegistry
RuleTriggerRegistry
ConditionRegistry
ActionRegistry
```

The rule, condition and action registries should preferably be shared with the rest of RPG Framework rather than being effect-specific.

---

# Recommended First Version

A strong V1 should include:

## Identity

- ID
- Name
- Description
- Icon
- Color

## Classification

- Beneficial
- Harmful
- Neutral
- Tags

## Duration

- Timed
- Infinite
- Default duration

## Level

- Optional amplifier

## Stacking

- Replace
- Refresh
- Extend
- Stack count
- Maximum stacks

## Modifiers

- Minecraft attribute modifiers
- Stack scaling
- Level scaling

## Rules

- Interval trigger
- Shared conditions
- Shared actions

## Events

- On Applied
- On Removed
- On Expired
- On Stack Added
- On Maximum Stacks

## Restrictions

- Movement
- Jumping
- Attacking
- Ability usage
- Item usage

## Conditions

- Has effect
- Effect level
- Stack count
- Category
- Tag

## Actions

- Apply effect
- Remove effect
- Add stack
- Remove stack
- Remove category
- Remove tag

## Display

- Icon
- Duration
- Level
- Stacks
- Hidden effects

## Custom Entity Integration

- Specific effect immunity
- Tag-based immunity

This is enough to create a large range of RPG status mechanics.

---

# Later Versions

Future expansions can add:

- Independent stack durations
- Effect parameters
- Advanced resistance
- Conditional visuals
- Screen overlays
- Custom render hooks
- Stack-specific rules
- Expression-based scaling
- Source-specific behavior
- Advanced persistence
- Custom cleansing rules

---

# Core Design Principle

The framework should not hardcode:

```text
Poison
Bleeding
Stun
Freeze
Silence
Root
```

These should be created using generic systems.

Example:

```text
Stunned
=
Harmful Effect
+ Cannot Move
+ Cannot Attack
+ Cannot Use Abilities
+ 2 Second Duration
```

```text
Bleeding
=
Harmful Effect
+ Bleed Tag
+ Stackable
+ Periodic Damage
```

```text
Frozen
=
Harmful Effect
+ Ice Tag
+ Movement Modifier Per Stack
+ Maximum Stack Event
```

The framework only understands:

```text
Category
Tags
Duration
Level
Stacks
Modifiers
Restrictions
Rules
Events
Display
```

The creator decides what those mechanics mean.

This keeps the Custom Status Effects System flexible enough to support mechanics the framework developer never anticipated.
