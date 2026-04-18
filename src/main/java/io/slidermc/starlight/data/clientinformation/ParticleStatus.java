package io.slidermc.starlight.data.clientinformation;

public enum ParticleStatus {
    ALL(0),
    DECREASED(1),
    MINIMAL(2);

    private final int id;

    ParticleStatus(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static ParticleStatus getById(int id) {
        for (ParticleStatus particleStatus : values()) {
            if (particleStatus.getId() == id) {
                return particleStatus;
            }
        }
        return ALL;
    }
}
