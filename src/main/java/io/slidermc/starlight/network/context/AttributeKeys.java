package io.slidermc.starlight.network.context;

import io.netty.util.AttributeKey;

public class AttributeKeys {
    public static final AttributeKey<ConnectionContext> CONNECTION_CONTEXT = AttributeKey.newInstance("connection_context");
    public static final AttributeKey<DownstreamConnectionContext> DOWNSTREAM_CONNECTION_CONTEXT = AttributeKey.newInstance("downstream_connection_context");
}
