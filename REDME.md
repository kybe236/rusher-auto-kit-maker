# Auto Kit Maker

This Module Allows you to create kits from input chests

# Showcase
https://www.youtube.com/watch?v=rvEVD-3yPHk

# Setup
## Kit
To create a kit:
1. Create the kit in your upper 3 slot wows
2. write `*auto-kit add-kit <name>` in chat to save the kit
3. your kit is now saved

To delete a kit:
1. write `*auto-kit remove <name>` in chat to remove the kit
2. your kit is now removed

To list all kits:
1. write `*auto-kit list-kits` in chat

## Usage
To setup the result chest (will hold result kits)
1. look at the chest
2. type `*auto-kit resultchest` in chat

To setup the shulker chest (holds empty shulkers)
1. look at the chest
2. type `*auto-kit shulkerchest` in chat

To setup the storage chest (the one the kit will be made off)
1. disable `Active` under `AutoKitMaker`
2. enable `Add Chests` under `AutoKitMaker`
3. enable `AutoKitMaker`
4. open the chests

To start the kit making
1. disable `Add Chests` under `AutoKitMaker`
2. enable `Active` under `AutoKitMaker`
3. enable `AutoKitMaker`


# Debuging
If your trying to restart the kit maker:
1. disable the module
2. write `*auto-kit reset-state` in chat
3. this will make it no longer continue its last goal