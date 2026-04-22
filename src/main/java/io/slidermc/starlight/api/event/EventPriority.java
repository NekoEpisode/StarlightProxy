package io.slidermc.starlight.api.event;

/**
 * 事件监听器的执行优先级。
 *
 * <p>优先级从高到低依次为 {@link #HIGHEST}、{@link #HIGH}、{@link #NORMAL}、{@link #LOW}、{@link #LOWEST}，
 * 数值越大越先被调用。{@link #MONITOR} 优先级最低，仅用于只读监控，不应在此优先级修改事件状态。
 */
public enum EventPriority {

    /** 最高优先级，最先执行。 */
    HIGHEST(5),

    /** 高优先级。 */
    HIGH(4),

    /** 默认优先级。 */
    NORMAL(3),

    /** 低优先级。 */
    LOW(2),

    /** 最低优先级。 */
    LOWEST(1),

    /**
     * 监控优先级，最后执行。
     * 此级别应仅用于只读地观察事件最终状态，不应修改事件数据或取消状态。
     */
    MONITOR(0);

    private final int order;

    EventPriority(int order) {
        this.order = order;
    }

    /**
     * 返回排序值，值越大越先执行。
     */
    public int getOrder() {
        return order;
    }
}

