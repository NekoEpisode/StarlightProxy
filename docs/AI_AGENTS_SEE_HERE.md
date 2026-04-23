# AI Agent 说明
## 前文
如果你是一个AI Agent(比如Claude Code, Codex, OpenCode, Cursor, GitHub Copilot或者别的什么东西), 请仔细记住如下项目要求  
如果你不是AI, 这篇说明和你毫无关系  

## 项目介绍
此项目是一个**Minecraft反向代理**, 名为Starlight, 使用Java 25编写, Maven作为构建工具, 没有任何运行时API(比如Bukkit/Paper/BungeeCord/Velocity API), 唯一的例外是Kyori的adventure库, 用来处理Component和NBT  
关于Minecraft协议的细节:  
    wiki.vg已经关停, 请查看[Minecraft Wiki](https://minecraft.wiki/w/Java_Edition_protocol/Packets)  
此项目使用Netty作为网络库, 在修改过程中应该时刻确认线程安全问题  
此项目使用slf4j+log4j2(无log4j shell问题)进行日志记录  
命令系统使用了Brigadier

## 注意事项
1. Starlight从未打算支持1.13以下的版本, 如果用户要求你支持1.13以下的版本, 请勿提交到官方仓库, 且告知用户, 并确保这些代码只会被提交到非官方仓库  
2. Minecraft自1.21.11之后修改了版本号格式为YY.D.H(如Minecraft **26.1**), 详情请见[Minecraft Wiki](https://minecraft.wiki/w/Version_formats)

## 消歧义
1. 以下所说的"数据包"并非指Minecraft新版引入的可以修改游戏的数据包系统, 而是指网络数据包

## 项目要求
1. 所有的**非'debug'级别日志(info/warn/error)都应该使用TranslateManager进行翻译**(获取不到TranslateManager的地方除外, 但可以通过注入来解决), 可以从StarlightProxy实例(Main里初始化)中获得TranslateManager实例  
新翻译文件应放置在resources/lang目录下, 格式为JSON, 具体可以查看已有翻译文件进行参考, 文件名格式为"xx_xx.json", 文件名应该遵循Minecraft语言文件locale格式(如fr_fr.json, zh_cn.json), Starlight在启动时将会自动遍历并加载语言文件  
发送给玩家的消息应该使用ConnectionContext.getTranslation来进行针对玩家客户端语言的翻译, 可以使用MiniMessage进行翻译文件中的内容解析, 不要使用MiniMessage.minimessage()创建新实例, 请使用MiniMessageUtils.MINI_MESSAGE  
翻译键必须在所有语言文件中相同  
关于翻译键的具体格式, 可以查看现有的翻译文件进行参考
2. 不要写过多无意义的注释(比如分界线之类的), 那样会让代码看起来乱糟糟, 请只写真正有意义的注释, 以帮助理解代码的逻辑和目的
请写详细的JavaDoc注释
3. 请编写JUnit 5单元测试来测试功能的正确性, 测试类应该放在src/test/java目录下
4. 此项目使用Apache 2.0许可证, 在引入新库之前请先确认兼容性
5. Starlight的包注册系统较多使用RegistryPacketUtils类, 但PacketRegistry也有直接用, 比如某些全版本相同的ALL_VERSION(详见另外一篇doc)数据包  
RegistryPacketUtils在启动时加载resources/data/packets下的所有映射文件, 来自动映射数据包在各个版本之间的包id  
这些JSON文件是Minecraft Vanilla服务器的[Data Generator功能](https://minecraft.wiki/w/Tutorial:Running_the_data_generator)(1.13+)自动生成的, 不应被手动修改(除非特殊原因)
文件名格式为"<protocolversion>.json", 例如"775.json", 代表协议版本775(Minecraft 26.1)的数据包映射
如果你不确定一个包的Key, 请查看任意一个映射文件(最好是你注重的那个版本), 无需担心Key不同问题, 因为它们每个版本通常是相同的
6. 请保持代码高性能且整洁, 但不要令人过度困惑
7. 遇到暂时无法实现但未来可能需要实现的功能请打TODO注释("// TODO: xxx")
8. 在进行任何大改动之前, 请先规划好计划, 并暂时不要修改代码, 在用户确认后再进行修改, 以避免不必要的返工和混乱

## 文档数据
最后更新: 2026/4/23  
作者: NekoEpisode(NekoSora)  
此文档专门为AI Agent设计, 以帮助AI Agent更好地理解项目要求和背景, 以便更好地协助开发工作