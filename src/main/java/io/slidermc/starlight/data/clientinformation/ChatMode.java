package io.slidermc.starlight.data.clientinformation;

public enum ChatMode {
    ENABLED(0),
    COMMANDS_ONLY(1),
    HIDDEN(2);

    private final int id;

    ChatMode(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static ChatMode getById(int id) {
        for (ChatMode mode : values()) {
            if (mode.getId() == id) {
                return mode;
            }
        }
        return null;
    }
}
