package io.slidermc.starlight.data.clientinformation;

import io.slidermc.starlight.utils.UnsignedByte;

import java.util.Objects;

public class ClientInformation {
    private String locale;
    private byte viewDistance;
    private ChatMode chatMode;
    private boolean chatColors;
    private UnsignedByte skinParts;
    private MainHand mainHand;
    private boolean enableTextFiltering;
    private boolean allowServerListing;
    private ParticleStatus particleStatus;

    public ClientInformation() {}

    public ClientInformation(String locale, byte viewDistance, ChatMode chatMode,
                             boolean chatColors, UnsignedByte skinParts, MainHand mainHand,
                             boolean enableTextFiltering, boolean allowServerListing, ParticleStatus particleStatus) {
        this.locale = locale;
        this.viewDistance = viewDistance;
        this.chatMode = chatMode;
        this.chatColors = chatColors;
        this.skinParts = skinParts;
        this.mainHand = mainHand;
        this.enableTextFiltering = enableTextFiltering;
        this.allowServerListing = allowServerListing;
        this.particleStatus = particleStatus;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public byte getViewDistance() {
        return viewDistance;
    }

    public void setViewDistance(byte viewDistance) {
        this.viewDistance = viewDistance;
    }

    public ChatMode getChatMode() {
        return chatMode;
    }

    public void setChatMode(ChatMode chatMode) {
        this.chatMode = chatMode;
    }

    public boolean isChatColors() {
        return chatColors;
    }

    public void setChatColors(boolean chatColors) {
        this.chatColors = chatColors;
    }

    public UnsignedByte getSkinParts() {
        return skinParts;
    }

    public void setSkinParts(UnsignedByte skinParts) {
        this.skinParts = skinParts;
    }

    public boolean isEnableTextFiltering() {
        return enableTextFiltering;
    }

    public void setEnableTextFiltering(boolean enableTextFiltering) {
        this.enableTextFiltering = enableTextFiltering;
    }

    public boolean isAllowServerListing() {
        return allowServerListing;
    }

    public void setAllowServerListing(boolean allowServerListing) {
        this.allowServerListing = allowServerListing;
    }

    public ParticleStatus getParticleStatus() {
        return particleStatus;
    }

    public void setParticleStatus(ParticleStatus particleStatus) {
        this.particleStatus = particleStatus;
    }

    public MainHand getMainHand() {
        return mainHand;
    }

    public void setMainHand(MainHand mainHand) {
        this.mainHand = mainHand;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ClientInformation that = (ClientInformation) o;
        return viewDistance == that.viewDistance && chatColors == that.chatColors && enableTextFiltering == that.enableTextFiltering && allowServerListing == that.allowServerListing && Objects.equals(locale, that.locale) && chatMode == that.chatMode && Objects.equals(skinParts, that.skinParts) && mainHand == that.mainHand && particleStatus == that.particleStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(locale, viewDistance, chatMode, chatColors, skinParts, mainHand, enableTextFiltering, allowServerListing, particleStatus);
    }

    @Override
    public String toString() {
        return "ClientInformation{" +
                "locale='" + locale + '\'' +
                ", viewDistance=" + viewDistance +
                ", chatMode=" + chatMode +
                ", chatColors=" + chatColors +
                ", skinParts=" + skinParts +
                ", mainHand=" + mainHand +
                ", enableTextFiltering=" + enableTextFiltering +
                ", allowServerListings=" + allowServerListing +
                ", particleStatus=" + particleStatus +
                '}';
    }
}
