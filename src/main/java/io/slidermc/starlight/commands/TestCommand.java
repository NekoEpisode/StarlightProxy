package io.slidermc.starlight.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.slidermc.starlight.api.command.StarlightCommand;
import io.slidermc.starlight.api.command.source.IStarlightCommandSource;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

public class TestCommand extends StarlightCommand {
    public TestCommand() {
        super("sliderproxytest");
    }

    @Override
    public LiteralArgumentBuilder<IStarlightCommandSource> build() {
        return literal(getName())
                .executes(ctx -> {
                    ctx.getSource().sendMessage(Component.text("This is a test command from SliderProxy!"));
                    return 0;
                })
                .then(RequiredArgumentBuilder.<IStarlightCommandSource, String>argument("brand", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            if (ctx.getSource().asProxiedPlayer().isPresent()) {
                                String brand = StringArgumentType.getString(ctx, "brand");
                                ByteBuf byteBuf = Unpooled.buffer();
                                try {
                                    MinecraftCodecUtils.writeString(byteBuf, brand);
                                    byte[] data = new byte[byteBuf.readableBytes()];
                                    byteBuf.readBytes(data);
                                    ctx.getSource().asProxiedPlayer().get().sendPluginMessage(Key.key("minecraft:brand"), data);
                                    ctx.getSource().sendMessage(Component.text("已发送brand: " + brand));
                                } finally {
                                    byteBuf.release();
                                }
                            } else {
                                ctx.getSource().sendMessage(Component.text("这个命令只能是玩家使用！"));
                            }
                            return 0;
                        })
                );
    }
}
