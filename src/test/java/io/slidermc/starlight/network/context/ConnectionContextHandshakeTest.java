package io.slidermc.starlight.network.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 下游握手地址的解析规则测试。
 *
 * <p>只覆盖真正会出错的规则：{@code ?query} 必须剥离（它是代理侧虚拟主机参数），
 * 而 {@code \0} 之后附加的数据段必须原样保留——后者承载 Floodgate 等插件的加密数据，
 * 一旦被切掉会静默失效。
 */
class ConnectionContextHandshakeTest {

    private static final String FLOODGATE_PAYLOAD = "play.example.com\0^Floodgate^Aabc?def&ghi";

    private ConnectionContext newContext() {
        return new ConnectionContext(null, null);
    }

    @Test
    void stripsQueryStringButKeepsNullSeparatedPayload() {
        ConnectionContext context = newContext();
        context.setHandshakeAddress(FLOODGATE_PAYLOAD);

        assertEquals("play.example.com\0^Floodgate^Aabc", context.getEffectiveDownstreamAddress());
    }

    @Test
    void explicitAddressIsUsedVerbatim() {
        ConnectionContext context = newContext();
        context.setHandshakeAddress("play.example.com");

        // 插件自己写入的内容不经过任何加工
        context.setDownstreamAddress(FLOODGATE_PAYLOAD);

        assertEquals(FLOODGATE_PAYLOAD, context.getEffectiveDownstreamAddress());
    }

    @Test
    void noAddressMeansFallBackToBackendConfiguredAddress() {
        assertNull(newContext().getEffectiveDownstreamAddress());
    }
}
