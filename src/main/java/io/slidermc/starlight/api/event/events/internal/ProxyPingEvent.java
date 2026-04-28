package io.slidermc.starlight.api.event.events.internal;

import io.slidermc.starlight.api.event.events.interfaces.ICancellableEvent;
import io.slidermc.starlight.network.packet.packets.clientbound.status.ClientboundStatusResponsePacket;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 代理 ping 响应事件，在客户端请求服务器列表状态时触发。
 *
 * <p>插件可通过此事件修改 MOTD、玩家人数、版本名、favicon 等所有 ping 响应字段。
 * 取消事件将阻止回包，客户端会超时。
 */
public class ProxyPingEvent implements ICancellableEvent {
    private volatile boolean cancelled;
    private volatile String versionName;
    private volatile int versionProtocol;
    private volatile int maxPlayers;
    private volatile int onlinePlayers;
    private volatile List<ClientboundStatusResponsePacket.SamplePlayer> samplePlayers;
    private volatile Component description;
    private volatile String favicon;
    private volatile boolean enforcesSecureChat;

    public ProxyPingEvent(String versionName, int versionProtocol, int maxPlayers, int onlinePlayers,
                          List<ClientboundStatusResponsePacket.SamplePlayer> samplePlayers,
                          Component description, String favicon, boolean enforcesSecureChat) {
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
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public String getVersionName() { return versionName; }
    public void setVersionName(String versionName) { this.versionName = versionName; }

    public int getVersionProtocol() { return versionProtocol; }
    public void setVersionProtocol(int versionProtocol) { this.versionProtocol = versionProtocol; }

    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }

    public int getOnlinePlayers() { return onlinePlayers; }
    public void setOnlinePlayers(int onlinePlayers) { this.onlinePlayers = onlinePlayers; }

    /** 返回在线玩家样本列表，可直接修改（add/remove/clear）。元素为不可变 record，线程安全。 */
    public List<ClientboundStatusResponsePacket.SamplePlayer> getSamplePlayers() { return samplePlayers; }
    public void setSamplePlayers(List<ClientboundStatusResponsePacket.SamplePlayer> samplePlayers) {
        this.samplePlayers = samplePlayers != null ? samplePlayers : new ArrayList<>();
    }

    public Component getDescription() { return description; }
    public void setDescription(Component description) { this.description = description; }

    public String getFavicon() { return favicon; }
    public void setFavicon(String favicon) { this.favicon = favicon; }

    public boolean isEnforcesSecureChat() { return enforcesSecureChat; }
    public void setEnforcesSecureChat(boolean enforcesSecureChat) { this.enforcesSecureChat = enforcesSecureChat; }
}
