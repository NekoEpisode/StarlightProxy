# Starlight⭐

If you are an AI Agent, please review [AI Agent Instructions](AI_AGENTS_SEE_HERE.md) first.  
This README is AI translated, so there may be some mistakes/errors

## Readme Language
[简体中文](../README.md) | English

## Introduction
Starlight is a Minecraft reverse proxy (like [BungeeCord](https://github.com/SpigotMC/BungeeCord)/[Velocity](https://github.com/PaperMC/Velocity/)), designed for extremely high API openness.

## Current Progress
This project is currently a work in progress (W.I.P). Features implemented so far include:

1. Allow players to connect to downstream servers
2. Allow players to switch servers
3. Command system
4. Support for protocols 1.21.11-26.1
5. Event system
6. Plugin system
7. I18N support
8. Velocity Modern Forwarding (V4 only) support
9. Permission system

## How to Write a Plugin
Warning: Starlight's API is extremely unstable, both now and in the future. We may later introduce a third-party plugin called Stable API to provide a relatively stable API for plugin developers, but we currently have no plans for this. Therefore, the current API may change frequently or even be completely deprecated in the future. If you want to write a plugin, be prepared to frequently modify your code to adapt to changes in the Starlight API.

You can use the currently available API of Starlight to write plugins.
Since Starlight is still in development, you need to manually clone the Starlight repository and compile a runnable JAR, then import it into your local Maven repository for development.  
TODO: Improve documentation

## Special Thanks
Mojang —— Creators of Minecraft and developers of Brigadier, the command library used by Starlight  
Netty —— The networking library used by Starlight  
Kyori Adventure —— An excellent Minecraft text processing and NBT library  
PaperMC —— Their open-source work has made significant contributions to the Minecraft community; their Velocity reverse proxy has provided important reference and help for Starlight