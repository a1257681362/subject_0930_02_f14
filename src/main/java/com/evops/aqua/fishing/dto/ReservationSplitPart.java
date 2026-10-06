package com.evops.aqua.fishing.dto;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 预约二次拆分明细：每段计划量必须大于0；收货点可指定（按收货点拆分），
 * 不指定则沿用母预约收货点。金额由服务端按行原单价分劈，客户端不得传价。
 */
public class ReservationSplitPart {

    @NotNull(message = "拆分计划量不能为空")
    private BigDecimal plannedQtyKg;

    private Long deliveryPointId;

    public BigDecimal getPlannedQtyKg() { return plannedQtyKg; }
    public void setPlannedQtyKg(BigDecimal plannedQtyKg) { this.plannedQtyKg = plannedQtyKg; }
    public Long getDeliveryPointId() { return deliveryPointId; }
    public void setDeliveryPointId(Long deliveryPointId) { this.deliveryPointId = deliveryPointId; }
}
