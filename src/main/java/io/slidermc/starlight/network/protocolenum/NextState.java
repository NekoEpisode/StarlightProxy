package io.slidermc.starlight.network.protocolenum;

public enum NextState {
    STATUS(1),
    LOGIN(2),
    TRANSFER(3),

    UNKNOWN(-1);

    private final int id;

    NextState(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static NextState getById(int id) {
        for (NextState nextState : values()) {
            if (nextState.getId() == id) {
                return nextState;
            }
        }
        return UNKNOWN;
    }
}
