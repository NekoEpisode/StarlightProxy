package io.slidermc.starlight.api.command;

import net.kyori.adventure.key.Key;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record CommandMeta(
        Key key,
        String description,
        String usage,
        boolean descriptionAsKey,
        boolean usageAsKey,
        Set<String> aliases) {

    public String name() {
        return key.value();
    }

    public String namespace() {
        return key.namespace();
    }

    public static Builder builder(String namespace, String name) {
        return new Builder(namespace, name);
    }

    public static class Builder {
        private final Key key;
        private String description = "";
        private String usage = "";
        private boolean descriptionAsKey;
        private boolean usageAsKey;
        private final Set<String> aliases = new LinkedHashSet<>();

        private Builder(String namespace, String name) {
            this.key = Key.key(namespace, name.toLowerCase());
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
            return new CommandMeta(key, description, usage,
                    descriptionAsKey, usageAsKey,
                    Collections.unmodifiableSet(new LinkedHashSet<>(aliases)));
        }
    }
}
