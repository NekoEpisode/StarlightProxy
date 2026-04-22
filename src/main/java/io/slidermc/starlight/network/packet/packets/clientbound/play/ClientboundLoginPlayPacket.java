package io.slidermc.starlight.network.packet.packets.clientbound.play;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.DownstreamConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;

import java.util.ArrayList;
import java.util.List;

public class ClientboundLoginPlayPacket implements IMinecraftPacket {
    public int entityId;
    public boolean isHardcore;
    public List<String> dimensionNames = new ArrayList<>();
    public int maxPlayers;
    public int viewDistance;
    public int simulationDistance;
    public boolean reducedDebugInfo;
    public boolean enableRespawnScreen;
    public boolean doLimitedCrafting;
    public int dimensionType; // VarInt
    public String dimensionName;
    public long hashedSeed;
    public int gameMode; // Unsigned Byte
    public byte previousGameMode; // -1 undefined
    public boolean isDebug;
    public boolean isFlat;
    public boolean hasDeathLocation;
    public String deathDimensionName; // optional
    public int[] deathLocation;       // optional, [x, y, z]
    public int portalCooldown; // VarInt
    public int seaLevel;       // VarInt
    public boolean enforcesSecureChat;

    public ClientboundLoginPlayPacket() {}

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        byteBuf.writeInt(entityId);
        byteBuf.writeBoolean(isHardcore);

        MinecraftCodecUtils.writeVarInt(byteBuf, dimensionNames.size());
        for (String dim : dimensionNames) {
            MinecraftCodecUtils.writeString(byteBuf, dim);
        }

        MinecraftCodecUtils.writeVarInt(byteBuf, maxPlayers);
        MinecraftCodecUtils.writeVarInt(byteBuf, viewDistance);
        MinecraftCodecUtils.writeVarInt(byteBuf, simulationDistance);
        byteBuf.writeBoolean(reducedDebugInfo);
        byteBuf.writeBoolean(enableRespawnScreen);
        byteBuf.writeBoolean(doLimitedCrafting);

        MinecraftCodecUtils.writeVarInt(byteBuf, dimensionType);
        MinecraftCodecUtils.writeString(byteBuf, dimensionName);
        byteBuf.writeLong(hashedSeed);

        byteBuf.writeByte(gameMode);
        byteBuf.writeByte(previousGameMode);

        byteBuf.writeBoolean(isDebug);
        byteBuf.writeBoolean(isFlat);

        byteBuf.writeBoolean(hasDeathLocation);
        if (hasDeathLocation) {
            MinecraftCodecUtils.writeString(byteBuf, deathDimensionName);
            MinecraftCodecUtils.writePosition(byteBuf, deathLocation[0], deathLocation[1], deathLocation[2]);
        }

        MinecraftCodecUtils.writeVarInt(byteBuf, portalCooldown);
        MinecraftCodecUtils.writeVarInt(byteBuf, seaLevel);
        byteBuf.writeBoolean(enforcesSecureChat);
    }

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        entityId = byteBuf.readInt();
        isHardcore = byteBuf.readBoolean();

        int dimensionCount = MinecraftCodecUtils.readVarInt(byteBuf);
        dimensionNames.clear();
        for (int i = 0; i < dimensionCount; i++) {
            dimensionNames.add(MinecraftCodecUtils.readString(byteBuf));
        }

        maxPlayers = MinecraftCodecUtils.readVarInt(byteBuf);
        viewDistance = MinecraftCodecUtils.readVarInt(byteBuf);
        simulationDistance = MinecraftCodecUtils.readVarInt(byteBuf);
        reducedDebugInfo = byteBuf.readBoolean();
        enableRespawnScreen = byteBuf.readBoolean();
        doLimitedCrafting = byteBuf.readBoolean();

        dimensionType = MinecraftCodecUtils.readVarInt(byteBuf);
        dimensionName = MinecraftCodecUtils.readString(byteBuf);
        hashedSeed = byteBuf.readLong();

        gameMode = byteBuf.readUnsignedByte();
        previousGameMode = byteBuf.readByte();

        isDebug = byteBuf.readBoolean();
        isFlat = byteBuf.readBoolean();

        hasDeathLocation = byteBuf.readBoolean();
        if (hasDeathLocation) {
            deathDimensionName = MinecraftCodecUtils.readString(byteBuf);
            deathLocation = MinecraftCodecUtils.readPosition(byteBuf);
        }

        portalCooldown = MinecraftCodecUtils.readVarInt(byteBuf);
        seaLevel = MinecraftCodecUtils.readVarInt(byteBuf);
        enforcesSecureChat = byteBuf.readBoolean();
    }

    public int getEntityId() {
        return entityId;
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
    }

    public boolean isHardcore() {
        return isHardcore;
    }

    public void setHardcore(boolean hardcore) {
        isHardcore = hardcore;
    }

    public List<String> getDimensionNames() {
        return dimensionNames;
    }

    public void setDimensionNames(List<String> dimensionNames) {
        this.dimensionNames = dimensionNames;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public int getViewDistance() {
        return viewDistance;
    }

    public void setViewDistance(int viewDistance) {
        this.viewDistance = viewDistance;
    }

    public int getSimulationDistance() {
        return simulationDistance;
    }

    public void setSimulationDistance(int simulationDistance) {
        this.simulationDistance = simulationDistance;
    }

    public boolean isReducedDebugInfo() {
        return reducedDebugInfo;
    }

    public void setReducedDebugInfo(boolean reducedDebugInfo) {
        this.reducedDebugInfo = reducedDebugInfo;
    }

    public boolean isEnableRespawnScreen() {
        return enableRespawnScreen;
    }

    public void setEnableRespawnScreen(boolean enableRespawnScreen) {
        this.enableRespawnScreen = enableRespawnScreen;
    }

    public boolean isDoLimitedCrafting() {
        return doLimitedCrafting;
    }

    public void setDoLimitedCrafting(boolean doLimitedCrafting) {
        this.doLimitedCrafting = doLimitedCrafting;
    }

    public int getDimensionType() {
        return dimensionType;
    }

    public void setDimensionType(int dimensionType) {
        this.dimensionType = dimensionType;
    }

    public String getDimensionName() {
        return dimensionName;
    }

    public void setDimensionName(String dimensionName) {
        this.dimensionName = dimensionName;
    }

    public long getHashedSeed() {
        return hashedSeed;
    }

    public void setHashedSeed(long hashedSeed) {
        this.hashedSeed = hashedSeed;
    }

    public int getGameMode() {
        return gameMode;
    }

    public void setGameMode(int gameMode) {
        this.gameMode = gameMode;
    }

    public byte getPreviousGameMode() {
        return previousGameMode;
    }

    public void setPreviousGameMode(byte previousGameMode) {
        this.previousGameMode = previousGameMode;
    }

    public boolean isDebug() {
        return isDebug;
    }

    public void setDebug(boolean debug) {
        isDebug = debug;
    }

    public boolean isFlat() {
        return isFlat;
    }

    public void setFlat(boolean flat) {
        isFlat = flat;
    }

    public boolean isHasDeathLocation() {
        return hasDeathLocation;
    }

    public void setHasDeathLocation(boolean hasDeathLocation) {
        this.hasDeathLocation = hasDeathLocation;
    }

    public String getDeathDimensionName() {
        return deathDimensionName;
    }

    public void setDeathDimensionName(String deathDimensionName) {
        this.deathDimensionName = deathDimensionName;
    }

    public int[] getDeathLocation() {
        return deathLocation;
    }

    public void setDeathLocation(int[] deathLocation) {
        this.deathLocation = deathLocation;
    }

    public int getPortalCooldown() {
        return portalCooldown;
    }

    public void setPortalCooldown(int portalCooldown) {
        this.portalCooldown = portalCooldown;
    }

    public int getSeaLevel() {
        return seaLevel;
    }

    public void setSeaLevel(int seaLevel) {
        this.seaLevel = seaLevel;
    }

    public boolean isEnforcesSecureChat() {
        return enforcesSecureChat;
    }

    public void setEnforcesSecureChat(boolean enforcesSecureChat) {
        this.enforcesSecureChat = enforcesSecureChat;
    }

    public static class Listener implements IPacketListener<ClientboundLoginPlayPacket> {
        @Override
        public void handle(ClientboundLoginPlayPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            DownstreamConnectionContext downstreamConnectionContext = ctx.channel().attr(AttributeKeys.DOWNSTREAM_CONNECTION_CONTEXT).get();
            Channel playerChannel = downstreamConnectionContext.getClient().getPlayerChannel();
            playerChannel.writeAndFlush(packet).addListener(_ -> {
                playerChannel.attr(AttributeKeys.CONNECTION_CONTEXT).get().getPlayer().sendAllPendingMessages(); // 发送所有积压的消息
            });
        }
    }
}
