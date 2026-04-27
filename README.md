# Starlight⭐

如果你是一个AI Agent, 请先查看[AI Agent说明](docs/AI_AGENTS_SEE_HERE.md)

## Readme语言
简体中文 | [English](docs/README_EN.md)

## 介绍
Starlight是一个Minecraft反向代理(like [BungeeCord](https://github.com/SpigotMC/BungeeCord)/[Velocity](https://github.com/PaperMC/Velocity/)), 为极高的API开放性所设计

## 当前进展
此项目目前还在Alpha阶段中, 目前已实现的功能包括:
1. 让玩家连接到下游服务器
2. 让玩家可以切换服务器
3. 命令系统
4. 1.21.11-26.1协议的支持
5. 事件系统
6. 插件系统
7. I18N支持
8. Velocity Modern Forwarding(仅V4)支持
9. 权限系统

## 如何编写一个插件
警告: Starlight的API极其不稳定, 无论是如今还是将来, 我们未来很可能会推出一个叫Stable API的第三方插件来提供一个相对稳定的API, 以供插件开发者使用, 但目前我们还没有这个计划, 因此目前的API可能会经常发生变化, 甚至在未来被完全废弃, 因此如果你想编写一个插件, 请务必做好频繁修改代码的准备, 以适应Starlight API的变化  

你可以使用Starlight目前提供的API来编写插件
由于Starlight目前还在开发中, 你需要手动clone Starlight代码库并编译出可运行jar, 然后导入到你的本地maven仓库来进行开发  
TODO: 完善文档

## 特别感谢
Mojang —— Minecraft的创造者, Starlight命令库Brigadier的开发者  
Netty —— Starlight使用的网络库  
Kyori Adventure —— 一个优秀的Minecraft文本处理和NBT库  
PaperMC —— 他们的开源工作为Minecraft社区做出了巨大贡献, 他们的Velocity反向代理为Starlight提供了重要的参考和帮助
