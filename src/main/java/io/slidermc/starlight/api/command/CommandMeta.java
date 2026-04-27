package io.slidermc.starlight.api.command;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record CommandMeta(
        String name,
        String description,
        String usage,
        boolean descriptionAsKey,
        boolean usageAsKey,
        Set<String> aliases) {

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private String description = "";
        private String usage = "";
        private boolean descriptionAsKey;
        private boolean usageAsKey;
        private final Set<String> aliases = new LinkedHashSet<>();

        private Builder(String name) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Command name must not be blank");
            }
            this.name = name.toLowerCase();
        }

        public Builder description(String desc) {
            this.description = desc;
            return this;
        }

        public Builder description(String desc, boolean asKey) {
            this.description = desc;
            this.descriptionAsKey = asKey;
            return this;
        }

        public Builder usage(String usage) {
            this.usage = usage;
            return this;
        }

        public Builder usage(String usage, boolean asKey) {
            this.usage = usage;
            this.usageAsKey = asKey;
            return this;
        }

        public Builder aliases(String... aliases) {
            if (aliases != null) {
                for (String alias : aliases) {
                    if (alias != null && !alias.isBlank()) {
                        this.aliases.add(alias.toLowerCase());
                    }
                }
            }
            return this;
        }

        public CommandMeta build() {
            return new CommandMeta(name, description, usage,
                    descriptionAsKey, usageAsKey,
                    Collections.unmodifiableSet(new LinkedHashSet<>(aliases)));
        }
    }
}
