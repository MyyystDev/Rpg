# Custom Entities System

## Overview

The **Custom Entities System** allows modpack creators to create fully configurable entities without writing Java code.

The framework provides one generic base entity:

```text
RpgEntity
```

Every actual custom entity is defined through JSON.

Examples:

```text
mypack:blacksmith
mypack:bandit
mypack:forest_guardian
mypack:quest_master
mypack:necromancer_boss
```

The same base entity can therefore behave as:

- NPC
- Merchant
- Quest giver
- Enemy
- Boss
- Guard
- Companion
- Animal
- Dummy
- Decorative character
- Scripted story NPC

The framework should not hardcode what an entity is supposed to be.

Instead, the JSON defines its:

```text
Identity
Appearance
Attributes
Movement
Combat
AI
Interactions
Dialogue
Equipment
Drops
Faction
Rules
Events
Persistence
Spawning
Boss behavior
```

---

# Design Goals

The system should be:

- **Data-driven**
- **Highly customizable**
- **Easy for basic NPCs**
- **Powerful enough for bosses and scripted characters**
- **Compatible with the RPG Framework's other systems**
- **Editable through JSON and the in-game editor**
- **Extensible through registries**
- **Component-oriented**

The most important design goal is:

> A creator should not need a different entity class for every type of NPC or monster.

---

# Entity Definition

Each entity is stored in its own JSON file.

Example:

```text
data/mypack/rpg/entities/blacksmith.json
data/mypack/rpg/entities/bandit.json
data/mypack/rpg/entities/ancient_guardian.json
```

Basic structure:

```text
Custom Entity
├── Identity
├── Appearance
├── Attributes
├── Movement
├── Equipment
├── AI
├── Combat
├── Interactions
├── Dialogue
├── Faction
├── Drops
├── Spawning
├── Rules
├── Events
└── Persistence
```

Only a few properties should be required.

---

# Identity

Every entity has a unique resource identifier.

Example:

```json
{
  "id": "mypack:blacksmith",

  "display": {
    "name": "Village Blacksmith",
    "name_visible": true
  }
}
```

Possible identity properties:

- ID
- Display name
- Description
- Name visibility
- Custom name color
- Boss name
- Tags
- Category

Tags could be useful for creator logic:

```json
{
  "tags": [
    "mypack:villagers",
    "mypack:merchants"
  ]
}
```

---

# Entity Templates

The editor should provide templates for common use cases.

Examples:

```text
Blank Entity
NPC
Merchant
Quest Giver
Hostile Mob
Guard
Companion
Boss
Training Dummy
Ambient Creature
```

Templates should only provide starting values.

They should not create different Java entity types.

---

# Appearance

Creators will probably consider this one of the most important sections.

Possible appearance options:

- Model
- Texture
- Scale
- Width
- Height
- Shadow size
- Eye height
- Nameplate
- Glow
- Tint
- Render layer
- Transparency
- Custom animations
- Equipment rendering

Example:

```json
{
  "appearance": {
    "model": "rpg:humanoid",
    "texture": "mypack:textures/entity/blacksmith.png",
    "scale": 1.0,

    "hitbox": {
      "width": 0.6,
      "height": 1.95
    }
  }
}
```

---

# Models

The system should support multiple model sources.

Possible built-in models:

```text
Humanoid
Zombie
Skeleton
Villager
Quadruped
Slime
Floating
Custom
```

Eventually creators should be able to use custom models.

A good architecture would allow model providers to be registered.

Example:

```json
{
  "model": {
    "type": "rpg:custom",
    "model": "mypack:models/entity/golem.json"
  }
}
```

If you later support something like GeckoLib or another animation system, it should ideally be implemented through an optional model provider rather than hardcoded everywhere.

---

# Textures

Creators should be able to define:

```text
Base texture
Alternative textures
Random texture
Conditional texture
Stage texture
Damaged texture
```

Example:

```json
{
  "textures": [
    {
      "texture": "mypack:textures/entity/bandit_1.png",
      "weight": 5
    },
    {
      "texture": "mypack:textures/entity/bandit_2.png",
      "weight": 2
    }
  ]
}
```

This allows entity variety without defining multiple entities.

---

# Scale

Creators should be able to change entity scale.

Example:

```json
{
  "appearance": {
    "scale": 1.5
  }
}
```

Scaling should ideally affect:

- Model
- Hitbox
- Eye height
- Shadow

Or creators could configure those separately.

---

# Attributes

Creators should be able to configure normal Minecraft attributes.

Example:

```json
{
  "attributes": {
    "minecraft:max_health": 40,
    "minecraft:movement_speed": 0.25,
    "minecraft:attack_damage": 6,
    "minecraft:armor": 4,
    "minecraft:follow_range": 32
  }
}
```

Useful attributes include:

- Max Health
- Movement Speed
- Attack Damage
- Attack Speed
- Armor
- Armor Toughness
- Knockback Resistance
- Follow Range
- Step Height

Your stat system could also eventually affect these.

Example:

```text
Strength 20
→ +4 Attack Damage
```

---

# Health

Health could have additional configuration.

Example:

```json
{
  "health": {
    "max": 100,
    "regeneration": {
      "amount": 1,
      "interval": 40
    }
  }
}
```

Potential options:

- Regeneration
- Invulnerability
- Damage immunity
- Damage resistance
- Damage multipliers

---

# Damage Rules

Creators may want certain entities to react differently to damage types.

Example:

```text
Fire damage       ×2
Magic damage      ×0.5
Fall damage       immune
Projectile damage ×1.5
```

Possible JSON:

```json
{
  "damage": {
    "modifiers": [
      {
        "type": "minecraft:in_fire",
        "multiplier": 2.0
      },
      {
        "type": "minecraft:fall",
        "immune": true
      }
    ]
  }
}
```

---

# Movement

Creators should be able to configure basic movement behavior.

Options:

```text
Walk
Fly
Swim
Hover
No movement
```

Properties:

- Movement speed
- Gravity
- Can jump
- Can climb
- Can swim
- Can fly
- Avoid water
- Avoid lava
- Door handling
- Pathfinding type

Example:

```json
{
  "movement": {
    "type": "ground",
    "can_open_doors": true,
    "can_swim": true
  }
}
```

---

# AI

AI should be highly configurable.

I would strongly recommend a **goal-based system** instead of hardcoding behaviors such as:

```text
if merchant
if guard
if monster
```

Example:

```json
{
  "ai": {
    "goals": [
      {
        "type": "rpg:look_at_player",
        "range": 8
      },
      {
        "type": "rpg:random_walk",
        "speed": 0.8
      }
    ]
  }
}
```

Possible AI goals:

- Random walk
- Look at player
- Look around
- Follow player
- Follow owner
- Guard position
- Patrol
- Return home
- Avoid entity
- Flee when low health
- Wander inside region
- Move toward target
- Stay still
- Sleep
- Sit

---

# Targeting AI

Target selection should be separate from normal movement goals.

Examples:

```text
Attack players
Attack hostile mobs
Attack faction enemies
Defend owner
Retaliate when attacked
Attack entities with tag
Attack only under conditions
```

Example:

```json
{
  "targeting": [
    {
      "type": "rpg:faction_enemy"
    },
    {
      "type": "rpg:retaliate"
    }
  ]
}
```

---

# Patrols

NPC creators will probably want patrol routes.

Example:

```text
Waypoint 1
    ↓
Waypoint 2
    ↓
Waypoint 3
    ↓
Waypoint 1
```

Configuration:

```json
{
  "patrol": {
    "loop": true,

    "points": [
      [10, 64, 20],
      [20, 64, 20],
      [20, 64, 40]
    ]
  }
}
```

Better still, patrols could eventually reference named waypoint paths stored separately.

---

# Home Position

NPCs should optionally have a home.

Example:

```text
Guard cannot wander more than 16 blocks from spawn.
```

Properties:

- Home position
- Home radius
- Return distance
- Teleport if too far

This is particularly useful for:

- Merchants
- Quest NPCs
- Guards
- Story NPCs

---

# Schedules

A feature creators would probably love is NPC schedules.

Example:

```text
06:00 → Walk to farm
12:00 → Go to tavern
18:00 → Return home
22:00 → Sleep
```

Possible schedule entry:

```json
{
  "time": 6000,
  "action": {
    "type": "rpg:move_to_location",
    "location": "mypack:tavern"
  }
}
```

Schedules could be optional and implemented through the generic rule/action system.

---

# Equipment

Creators should be able to configure equipment.

Example:

```json
{
  "equipment": {
    "mainhand": "minecraft:iron_sword",
    "offhand": "minecraft:shield",
    "head": "minecraft:iron_helmet",
    "chest": "minecraft:iron_chestplate"
  }
}
```

Possible features:

- Fixed items
- Weighted random items
- Item pools
- Custom item NBT/components
- Equipment visibility
- Drop chance

---

# Combat

Combat should be configurable separately from general AI.

Possible combat styles:

```text
None
Melee
Ranged
Magic
Hybrid
Custom
```

---

# Melee Combat

Properties:

- Attack range
- Attack cooldown
- Damage
- Knockback
- Chase speed
- Attack animation
- Attack sound

Example:

```json
{
  "combat": {
    "type": "melee",
    "range": 2.5,
    "cooldown": 20
  }
}
```

---

# Ranged Combat

Possible configuration:

```text
Projectile
Range
Cooldown
Accuracy
Projectile speed
Burst size
Spread
```

Example:

```json
{
  "combat": {
    "type": "ranged",

    "projectile": "minecraft:arrow",

    "range": 20,
    "cooldown": 30,
    "projectile_speed": 1.5
  }
}
```

---

# Custom Attacks

For advanced monsters and bosses, attacks should ideally use the framework's action system.

Example:

```text
Attack
├── Play animation
├── Wait 10 ticks
├── Spawn particles
├── Damage entities in radius
└── Play sound
```

This means custom combat can be created without adding dedicated Java attack classes.

---

# Abilities

Entities should eventually be able to reference your custom ability system.

Example:

```json
{
  "abilities": [
    "mypack:fireball",
    "mypack:teleport",
    "mypack:summon_skeletons"
  ]
}
```

The AI can then decide when those abilities are used.

---

# Interactions

Player interaction should be configurable.

Possible interactions:

```text
Dialogue
Shop
Quest
Command
Action
Mount
Inspect
Recruit
Open GUI
```

Example:

```json
{
  "interaction": {
    "type": "rpg:dialogue",
    "dialogue": "mypack:blacksmith_dialogue"
  }
}
```

---

# Multiple Interactions

An entity may need several possible interactions.

Example:

```text
Right Click
├── If quest available → Quest dialogue
├── Else if sneaking → Inspect
└── Else → Shop
```

This is where conditions and actions become useful.

---

# Dialogue Integration

Custom entities should integrate directly with the dialogue system.

Example:

```json
{
  "dialogue": {
    "default": "mypack:blacksmith"
  }
}
```

Conditional dialogues:

```text
If Reputation >= 50
    friendly_dialogue

If Corruption >= 80
    afraid_dialogue

Otherwise
    default_dialogue
```

---

# Shops

Entities should be able to reference custom shops.

Example:

```json
{
  "shop": "mypack:blacksmith_shop"
}
```

I would keep shop definitions separate from entity definitions.

That allows the same shop to be reused by several entities.

---

# Quests

Entities should integrate with quests.

Possible uses:

```text
Quest giver
Quest objective
Quest completion target
Quest dialogue character
Escort target
Kill target
```

Example:

```text
Quest:
Kill 10 mypack:bandit
```

Custom entity IDs should therefore be usable as quest conditions/objectives.

---

# Factions

Faction support is extremely useful.

Example factions:

```text
Kingdom
Bandits
Undead
Merchants
Player
```

Entity:

```json
{
  "faction": "mypack:bandits"
}
```

Faction relations:

```text
Bandits → hostile toward Kingdom
Kingdom → hostile toward Bandits
Merchants → neutral
```

Faction systems could determine:

- Target selection
- Dialogue
- Reputation
- Shops
- Guards
- Friendly fire
- Assistance

---

# Player Reputation

Factions can integrate with custom stats.

Example:

```text
Bandit Reputation = -80
```

Then:

```text
If Bandit Reputation <= -50
    attack player

If Bandit Reputation >= 25
    allow dialogue

If Bandit Reputation >= 50
    unlock shop
```

This would create powerful emergent behavior using systems you already have.

---

# Drops

Creators should be able to configure loot.

I recommend using Minecraft loot tables whenever possible.

Example:

```json
{
  "loot_table": "mypack:entities/bandit"
}
```

This avoids recreating Minecraft's entire loot system.

Additional properties could include:

- XP reward
- Custom RPG XP
- Currency
- Quest rewards

---

# Experience

Possible rewards:

```json
{
  "rewards": {
    "minecraft_xp": 10,
    "rpg_xp": 25
  }
}
```

If you later have multiple progression systems, this could instead use generic actions.

---

# Sounds

Creators should be able to configure entity sounds.

Examples:

- Ambient
- Hurt
- Death
- Attack
- Step
- Interaction

Example:

```json
{
  "sounds": {
    "ambient": "minecraft:entity.villager.ambient",
    "hurt": "minecraft:entity.villager.hurt",
    "death": "minecraft:entity.villager.death"
  }
}
```

---

# Animations

Possible animation states:

```text
Idle
Walk
Run
Attack
Hurt
Death
Interact
Cast
Sleep
Custom
```

The entity runtime can expose state changes while model providers decide how they are rendered.

---

# Events

Custom entities should expose events to the shared RPG event/action system.

Important events:

```text
On Spawn
On Load
On Interact
On Attack
On Hurt
On Kill
On Death
On Target Found
On Target Lost
On Low Health
On Enter Combat
On Leave Combat
On Player Nearby
On Player Leaves
```

Example:

```text
[Entity Death]
      ↓
[Play Sound]
      ↓
[Spawn Particles]
      ↓
[Start Quest]
```

---

# Rules

Like custom stats, entities should support generic:

```text
Trigger
   ↓
Conditions
   ↓
Actions
```

Example:

```text
When Health < 50%
    AND Phase == 1
        Set Phase = 2
        Play Sound
        Spawn 4 Skeletons
        Give Strength
```

This is what makes advanced enemies possible.

---

# Entity Variables

A very useful advanced feature would be custom per-entity variables.

Example:

```text
phase = 1
angry = false
player_met = true
ritual_progress = 3
```

Definition:

```json
{
  "variables": {
    "phase": 1,
    "angry": false
  }
}
```

Rules could read/write these variables.

This is especially useful for bosses and story NPCs.

---

# Global vs Per-Instance State

You should distinguish between:

```text
Entity Definition
```

and:

```text
Entity Instance
```

For example:

```text
Definition:
mypack:blacksmith

Instance A:
Village blacksmith
Quest completed = true

Instance B:
City blacksmith
Quest completed = false
```

The JSON defines the entity type.

World save data stores individual instance state.

---

# Persistence

Creators should control whether an entity persists.

Options:

```text
Persistent
Can despawn
Despawn when far away
Never despawn
Respawn after death
Unique NPC
```

Example:

```json
{
  "persistence": {
    "despawn": false,
    "save_instance": true
  }
}
```

---

# Respawning NPCs

Story NPCs often need respawn behavior.

Possible options:

```text
Never respawn
Respawn immediately
Respawn after X ticks
Respawn next day
Respawn when region resets
```

Example:

```json
{
  "respawn": {
    "enabled": true,
    "delay": 24000
  }
}
```

---

# Unique Entities

Creators may want NPCs that exist only once.

Example:

```text
King Aldric
The Necromancer
Quest Master
```

A unique entity should not accidentally spawn multiple copies.

Possible property:

```json
{
  "unique": true
}
```

The framework could track its instance UUID.

---

# Spawning

Entities need multiple spawning methods.

Possible modes:

```text
Natural spawning
Structure spawning
Region spawning
Manual placement
Command spawning
Quest spawning
Action spawning
Spawner spawning
```

---

# Spawn Rules

Example:

```json
{
  "spawn": {
    "natural": true,

    "weight": 10,

    "biomes": [
      "#minecraft:is_forest"
    ],

    "light": {
      "max": 7
    },

    "group": {
      "min": 2,
      "max": 5
    }
  }
}
```

Possible conditions:

- Biome
- Dimension
- Height
- Light
- Time
- Weather
- Region
- Nearby block
- Distance from player
- Difficulty
- Custom condition

---

# Manual NPC Placement

For Custom-NPC-style workflows, creators should also be able to place an entity directly in the world.

Possible workflow:

```text
Custom Entity Tool
        ↓
Select Entity
        ↓
Click Block
        ↓
Entity spawned
```

The placed entity becomes an instance of the selected definition.

---

# Spawn Overrides

Placed NPCs may need instance-specific overrides.

Example:

```text
Definition:
mypack:guard

Instance:
Name = West Gate Guard
Home Position = West Gate
Patrol = west_gate_patrol
```

This is very useful.

The base JSON remains reusable while individual placed NPCs can have overrides.

---

# Instance Overrides

Possible overrides:

- Display name
- Position
- Rotation
- Home location
- Dialogue
- Shop
- Faction
- Equipment
- Variables
- Patrol
- Invulnerability

You should be careful not to allow every property to become instance-specific, or instance data may become difficult to maintain.

---

# Boss Support

Bosses should not require a dedicated entity class.

Instead, bosses should be normal custom entities using advanced components.

Example:

```text
Boss
├── Boss Bar
├── High health
├── Custom attacks
├── Phase variables
├── Phase rules
├── Arena restrictions
└── Death events
```

---

# Boss Bar

Example:

```json
{
  "boss_bar": {
    "enabled": true,
    "name": "The Hollow King",
    "color": "purple",
    "style": "progress"
  }
}
```

Possible options:

- Color
- Style
- Visibility range
- Fog
- Darken sky
- Play boss music

---

# Boss Phases

Boss phases should ideally be implemented through variables and rules.

Example:

```text
Phase 1
Health > 70%

Phase 2
Health <= 70%

Phase 3
Health <= 30%
```

Rule:

```text
Trigger:
Health changed

Condition:
Health <= 70%
Phase == 1

Actions:
Set Phase = 2
Play animation
Summon minions
Change attacks
```

---

# Dynamic Configuration

Advanced creators may want properties to change during gameplay.

Examples:

```text
Change faction
Change dialogue
Change texture
Change AI
Change name
Enable attack
Disable movement
```

This can be done through entity variables and conditional components.

Example:

```text
If angry == true
    texture = angry_texture
    targeting = players
```

---

# Interaction Conditions

Almost every interaction should support conditions.

Example:

```text
Shop is available only if:
Reputation >= 20

Dialogue option appears only if:
Quest "lost_sword" completed

NPC attacks if:
Corruption >= 80
```

The same shared condition system should be reused across the framework.

---

# Actions

Entities should reuse the framework's generic action system.

Possible actions:

```text
Modify stat
Start quest
Complete quest
Give item
Remove item
Teleport
Play sound
Spawn particles
Spawn entity
Run command
Set variable
Change faction
Open dialogue
Open shop
Damage entity
Heal entity
```

This avoids implementing entity-only logic.

---

# Commands

Useful commands might include:

```text
/rpg entity spawn <id>

/rpg entity give_tool

/rpg entity reload

/rpg entity inspect

/rpg entity edit

/rpg entity remove
```

Creators will probably want an easy way to identify the definition ID of an entity they are looking at.

---

# Debugging

Developer tools should expose information such as:

```text
Entity ID
Instance UUID
Current Health
Current Target
Current AI Goal
Faction
Variables
Active Rules
Home Position
Current Patrol Point
```

A debug overlay would make complex NPC behavior much easier to troubleshoot.

---

# Example: Blacksmith

```json
{
  "id": "mypack:blacksmith",

  "display": {
    "name": "Blacksmith",
    "name_visible": true
  },

  "appearance": {
    "model": "rpg:humanoid",
    "texture": "mypack:textures/entity/blacksmith.png"
  },

  "attributes": {
    "minecraft:max_health": 30,
    "minecraft:movement_speed": 0.22
  },

  "ai": {
    "goals": [
      {
        "type": "rpg:random_walk",
        "speed": 0.6
      },
      {
        "type": "rpg:look_at_player",
        "range": 8
      }
    ]
  },

  "faction": "mypack:village",

  "interaction": {
    "type": "rpg:dialogue",
    "dialogue": "mypack:blacksmith"
  },

  "shop": "mypack:blacksmith_shop",

  "persistence": {
    "despawn": false
  }
}
```

---

# Example: Bandit

```json
{
  "id": "mypack:bandit",

  "display": {
    "name": "Bandit"
  },

  "appearance": {
    "model": "rpg:humanoid",
    "texture": "mypack:textures/entity/bandit.png"
  },

  "attributes": {
    "minecraft:max_health": 24,
    "minecraft:movement_speed": 0.27,
    "minecraft:attack_damage": 5
  },

  "equipment": {
    "mainhand": "minecraft:iron_sword"
  },

  "ai": {
    "goals": [
      {
        "type": "rpg:random_walk"
      }
    ]
  },

  "targeting": [
    {
      "type": "rpg:faction_enemy"
    }
  ],

  "combat": {
    "type": "melee"
  },

  "faction": "mypack:bandits",

  "loot_table": "mypack:entities/bandit"
}
```

---

# Example: Boss

```json
{
  "id": "mypack:hollow_king",

  "display": {
    "name": "The Hollow King"
  },

  "appearance": {
    "model": "mypack:hollow_king",
    "texture": "mypack:textures/entity/hollow_king.png",
    "scale": 1.5
  },

  "attributes": {
    "minecraft:max_health": 500,
    "minecraft:movement_speed": 0.25,
    "minecraft:attack_damage": 12,
    "minecraft:armor": 10
  },

  "boss_bar": {
    "enabled": true,
    "color": "purple"
  },

  "variables": {
    "phase": 1
  },

  "rules": [
    {
      "trigger": {
        "type": "rpg:health_changed"
      },

      "conditions": [
        {
          "type": "rpg:health_percentage",
          "operator": "<=",
          "value": 0.5
        },

        {
          "type": "rpg:variable",
          "variable": "phase",
          "operator": "==",
          "value": 1
        }
      ],

      "actions": [
        {
          "type": "rpg:set_variable",
          "variable": "phase",
          "value": 2
        },

        {
          "type": "rpg:spawn_entity",
          "entity": "mypack:hollow_minion",
          "count": 4
        }
      ]
    }
  ]
}
```

---

# Integration With the RPG Framework

Custom entities should connect directly to every major RPG system.

```text
Custom Stats
    ↓
NPC conditions and combat scaling

Dialogue
    ↓
Entity interactions

Quests
    ↓
Quest givers, targets and objectives

Shops
    ↓
Merchant entities

Factions
    ↓
Relationships and targeting

Abilities
    ↓
Custom enemy attacks

Zones
    ↓
Spawn rules and behavior

Loot
    ↓
Entity rewards

Rules
    ↓
Advanced behavior
```

The custom entity system could eventually become one of the central building blocks of the entire mod.

---

# Recommended Architecture

Internally I would separate four concepts.

```text
RpgEntity
    │
    ├── EntityDefinition
    │
    ├── EntityInstanceData
    │
    └── EntityRuntime
```

## EntityDefinition

Loaded from JSON.

Contains:

```text
Appearance
Attributes
AI
Combat
Interactions
Etc.
```

## EntityInstanceData

Saved per entity instance.

Contains:

```text
Definition ID
Variables
Home
Patrol progress
Persistent state
Overrides
```

## EntityRuntime

Handles things that happen during gameplay.

Examples:

```text
AI evaluation
Rule execution
Combat
Interactions
Stage changes
```

---

# Component-Based Architecture

I think this system would benefit heavily from components.

Conceptually:

```text
EntityDefinition
├── AppearanceComponent
├── AttributeComponent
├── AiComponent
├── CombatComponent
├── InteractionComponent
├── FactionComponent
├── EquipmentComponent
├── RuleComponent
└── SpawnComponent
```

Not every entity needs every component.

For example:

```text
Blacksmith
├── Appearance
├── Attributes
├── AI
├── Interaction
├── Shop
└── Faction
```

while:

```text
Boss
├── Appearance
├── Attributes
├── AI
├── Combat
├── BossBar
├── Variables
├── Rules
└── Loot
```

This should keep the system maintainable as it grows.

---

# Recommended First Version

I would **not** try to implement everything immediately.

A strong first version could contain:

## Entity Definition

- ID
- Name
- Texture
- Humanoid model
- Scale

## Attributes

- Health
- Movement speed
- Attack damage
- Armor
- Follow range

## AI

- Idle
- Wander
- Look at player
- Follow
- Guard position
- Attack target

## Combat

- Melee
- Basic ranged

## Equipment

- Main hand
- Off hand
- Armor

## Interaction

- Dialogue
- Shop
- Generic action

## Faction

- Faction assignment
- Basic hostility

## Drops

- Loot table

## Persistence

- Despawn / persistent

## Events

- Spawn
- Interact
- Hurt
- Death

## Spawning

- Command
- Editor placement
- Spawn action

That alone would already let creators build a huge number of RPG NPCs.

---

# Second Version

Then add:

```text
Patrols
Schedules
Natural spawning
Custom models
Animations
Custom attacks
Entity variables
Advanced rules
Conditional appearance
Companions
Respawning NPCs
Boss bars
Boss phases
Instance overrides
```

---

# Core Design Principle

The custom entity system should never ask:

```text
Is this entity a merchant?
Is this entity a boss?
Is this entity a guard?
```

Instead it should ask:

```text
What components does this entity have?
What rules does it run?
What interactions are available?
What does its AI do?
```

For example:

```text
"Boss"
```

should not really be an entity type.

It is simply:

```text
Custom Entity
+ Boss Bar
+ Large Health Pool
+ Combat AI
+ Custom Abilities
+ Phase Rules
```

Likewise:

```text
"Merchant"
```

is simply:

```text
Custom Entity
+ Interaction
+ Shop
+ Friendly AI
```

That approach gives modpack creators much more freedom and prevents the system from becoming a giant collection of hardcoded special cases.