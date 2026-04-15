package io.slidermc.starlight.network.packet.packets.clientbound.status;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClientboundStatusResponsePacket implements IMinecraftPacket {
    private static final GsonComponentSerializer serializer = GsonComponentSerializer.gson();

    private String versionName;
    private int versionProtocol;
    private int maxPlayers;
    private int onlinePlayers;
    private List<SamplePlayer> samplePlayers = new ArrayList<>();
    private Component description;
    /** nullable，无 favicon 时不写入 JSON */
    private String favicon;
    private boolean enforcesSecureChat;

    public ClientboundStatusResponsePacket() {}

    public ClientboundStatusResponsePacket(String versionName, int versionProtocol,
                                           int maxPlayers, int onlinePlayers,
                                           List<SamplePlayer> samplePlayers,
                                           Component description, String favicon,
                                           boolean enforcesSecureChat) {
        this.versionName = versionName;
        this.versionProtocol = versionProtocol;
        this.maxPlayers = maxPlayers;
        this.onlinePlayers = onlinePlayers;
        this.samplePlayers = samplePlayers != null ? samplePlayers : new ArrayList<>();
        this.description = description;
        this.favicon = favicon;
        this.enforcesSecureChat = enforcesSecureChat;
    }

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        JsonObject root = new JsonObject();

        JsonObject version = new JsonObject();
        version.addProperty("name", versionName);
        version.addProperty("protocol", versionProtocol);
        root.add("version", version);

        JsonObject players = new JsonObject();
        players.addProperty("max", maxPlayers);
        players.addProperty("online", onlinePlayers);
        JsonArray sample = new JsonArray();
        for (SamplePlayer sp : samplePlayers) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", sp.name());
            entry.addProperty("id", sp.id().toString());
            sample.add(entry);
        }
        players.add("sample", sample);
        root.add("players", players);

        // description 是 Chat Component，序列化为 JSON 对象后嵌入
        root.add("description", JsonParser.parseString(serializer.serialize(description)));

        if (favicon != null) {
            root.addProperty("favicon", favicon);
        }

        root.addProperty("enforcesSecureChat", enforcesSecureChat);

        MinecraftCodecUtils.writeString(byteBuf, root.toString());
    }

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        JsonObject root = JsonParser.parseString(MinecraftCodecUtils.readString(byteBuf)).getAsJsonObject();

        JsonObject version = root.getAsJsonObject("version");
        versionName = version.get("name").getAsString();
        versionProtocol = version.get("protocol").getAsInt();

        JsonObject players = root.getAsJsonObject("players");
        maxPlayers = players.get("max").getAsInt();
        onlinePlayers = players.get("online").getAsInt();
        samplePlayers = new ArrayList<>();
        if (players.has("sample")) {
            for (var element : players.getAsJsonArray("sample")) {
                JsonObject entry = element.getAsJsonObject();
                samplePlayers.add(new SamplePlayer(
                        entry.get("name").getAsString(),
                        UUID.fromString(entry.get("id").getAsString())
                ));
            }
        }

        description = serializer.deserialize(root.get("description").toString());

        favicon = root.has("favicon") ? root.get("favicon").getAsString() : null;

        enforcesSecureChat = root.has("enforcesSecureChat") && root.get("enforcesSecureChat").getAsBoolean();
    }

    public record SamplePlayer(String name, UUID id) {}

    public String getVersionName() { return versionName; }
    public void setVersionName(String versionName) { this.versionName = versionName; }

    public int getVersionProtocol() { return versionProtocol; }
    public void setVersionProtocol(int versionProtocol) { this.versionProtocol = versionProtocol; }

    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }

    public int getOnlinePlayers() { return onlinePlayers; }
    public void setOnlinePlayers(int onlinePlayers) { this.onlinePlayers = onlinePlayers; }

    public List<SamplePlayer> getSamplePlayers() { return samplePlayers; }
    public void setSamplePlayers(List<SamplePlayer> samplePlayers) { this.samplePlayers = samplePlayers; }

    public Component getDescription() { return description; }
    public void setDescription(Component description) { this.description = description; }

    public String getFavicon() { return favicon; }
    public void setFavicon(String favicon) { this.favicon = favicon; }

    public boolean isEnforcesSecureChat() { return enforcesSecureChat; }
    public void setEnforcesSecureChat(boolean enforcesSecureChat) { this.enforcesSecureChat = enforcesSecureChat; }

    public static class Listener implements IPacketListener<ClientboundStatusResponsePacket> {
        @Override
        public void handle(ClientboundStatusResponsePacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            // TODO: 待实现
        }
    }
}
