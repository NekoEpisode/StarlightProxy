package io.slidermc.starlight.network.packet.packets.serverbound.configuration;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.data.clientinformation.ChatMode;
import io.slidermc.starlight.data.clientinformation.ClientInformation;
import io.slidermc.starlight.data.clientinformation.MainHand;
import io.slidermc.starlight.data.clientinformation.ParticleStatus;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import io.slidermc.starlight.utils.UnsignedByte;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerboundClientInformationConfigurationPacket implements IMinecraftPacket {
    private static final Logger log = LoggerFactory.getLogger(ServerboundClientInformationConfigurationPacket.class);
    private ClientInformation information;

    public ServerboundClientInformationConfigurationPacket() {}

    public ServerboundClientInformationConfigurationPacket(ClientInformation information) {
        this.information = information;
    }

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        if (information.getLocale().length() > 16) throw new IllegalArgumentException("Locale.length cannot bigger than 16");
        MinecraftCodecUtils.writeString(byteBuf, information.getLocale());
        byteBuf.writeByte(information.getViewDistance());
        MinecraftCodecUtils.writeVarInt(byteBuf, information.getChatMode().getId());
        byteBuf.writeBoolean(information.isChatColors());
        byteBuf.writeByte(information.getSkinParts().value());
        MinecraftCodecUtils.writeVarInt(byteBuf, information.getMainHand().getId());
        byteBuf.writeBoolean(information.isEnableTextFiltering());
        byteBuf.writeBoolean(information.isAllowServerListing());
        MinecraftCodecUtils.writeVarInt(byteBuf, information.getParticleStatus().getId());
    }

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        String locale = MinecraftCodecUtils.readString(byteBuf);
        if (locale.length() > 16) return;
        byte viewDistance = byteBuf.readByte();
        ChatMode chatMode = ChatMode.getById(MinecraftCodecUtils.readVarInt(byteBuf));
        boolean chatColors = byteBuf.readBoolean();
        UnsignedByte skinParts = new UnsignedByte(byteBuf.readUnsignedByte());
        MainHand mainHand = MainHand.getById(MinecraftCodecUtils.readVarInt(byteBuf));
        boolean enableTextFiltering = byteBuf.readBoolean();
        boolean allowServerListing = byteBuf.readBoolean();
        ParticleStatus particleStatus = ParticleStatus.getById(MinecraftCodecUtils.readVarInt(byteBuf));
        this.information = new ClientInformation(locale, viewDistance, chatMode, chatColors, skinParts,
                mainHand, enableTextFiltering, allowServerListing, particleStatus);
    }

    public ClientInformation getInformation() {
        return information;
    }

    public void setInformation(ClientInformation information) {
        this.information = information;
    }

    public static class Listener implements IPacketListener<ServerboundClientInformationConfigurationPacket> {
        @Override
        public void handle(ServerboundClientInformationConfigurationPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            ConnectionContext context = ctx.channel().attr(AttributeKeys.CONNECTION_CONTEXT).get();
            context.setClientInformation(packet.getInformation()); // 设置information

            // 转发（下游尚未就绪时跳过——连接建立后 ServerboundLoginAckPacket 会补发）
            io.netty.channel.Channel downstream = context.getDownstreamChannel();
            if (downstream != null && downstream.isActive()) {
                downstream.writeAndFlush(packet);
            } else {
                log.debug("下游 channel 尚未就绪，ClientInformation 已缓存，稍后补发");
            }

            log.debug("已得到玩家的ClientInformation信息: {}", packet.information);
        }
    }
}
