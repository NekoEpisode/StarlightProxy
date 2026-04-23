package io.slidermc.starlight.command.source;

import io.slidermc.starlight.api.command.source.ContextKey;
import io.slidermc.starlight.api.command.source.IStarlightCommandSource;
import io.slidermc.starlight.api.player.ProxiedPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.ansi.ANSIComponentSerializer;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ConsoleCommandSource implements IStarlightCommandSource {
    private final Map<ContextKey<?>, Object> contextMap = new ConcurrentHashMap<>();

    @Override
    public void sendMessage(Component message) {
        String ansi = ANSIComponentSerializer.ansi().serialize(message);
        System.out.println(ansi);
    }

    @Override
    public Optional<ProxiedPlayer> asProxiedPlayer() {
        return Optional.empty();
    }

    @Override
    public <T> void setContext(ContextKey<T> key, T value) {
        contextMap.put(key, value);
    }

    @Override
    public <T> Optional<T> getContext(ContextKey<T> key) {
        Object value = contextMap.get(key);
        if (value == null) return Optional.empty();
        return Optional.of(key.type().cast(value));
    }

    @Override
    public Set<ContextKey<?>> contextKeys() {
        return Collections.unmodifiableSet(contextMap.keySet());
    }

    @Override
    public boolean hasPermission(String permission) {
        return true;
    }
}
