package io.slidermc.starlight.data.clientinformation;

public enum MainHand {
    LEFT(0),
    RIGHT(1);

    private final int id;

    MainHand(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static MainHand getById(int id) {
        for (MainHand mainHand : values()) {
            if (mainHand.getId() == id) {
                return mainHand;
            }
        }
        return null;
    }
}
