package com.evops.aqua.fishing.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户订单明细行：规格 + 重量区间 + 交付时间 + 计划量 + 收货点 + 单价，一行拆分为一条捕捞预约。
 * 行金额=计划量×单价，在创建时落账冻结；后续任何拆分都不改原订单金额口径。
 */
public class OrderLineRequest {

    @NotBlank(message = "规格不能为空")
    private String specCode;

    @NotNull(message = "重量区间下限不能为空")
    private BigDecimal weightMinG;

    @NotNull(message = "重量区间上限不能为空")
    private BigDecimal weightMaxG;

    @NotNull(message = "交付时间不能为空")
    private LocalDateTime deliveryTime;

    @NotNull(message = "计划量不能为空")
    private BigDecimal plannedQtyKg;

    /** 收货点：可空表示暂不指定；按规格和收货点拆分时各行收货点不同。 */
    private Long deliveryPointId;

    /**
     * 单价：可选。传入则行金额=计划量×单价并冻结；不传按0落账（兼容历史无金额口径订单），
     * 但金额一旦在创建时落账，后续任何拆分都不得改变。
     */
    private BigDecimal unitPrice;

    public String getSpecCode() { return specCode; }
    public void setSpecCode(String specCode) { this.specCode = specCode; }
    public BigDecimal getWeightMinG() { return weightMinG; }
    public void setWeightMinG(BigDecimal weightMinG) { this.weightMinG = weightMinG; }
    public BigDecimal getWeightMaxG() { return weightMaxG; }
    public void setWeightMaxG(BigDecimal weightMaxG) { this.weightMaxG = weightMaxG; }
    public LocalDateTime getDeliveryTime() { return deliveryTime; }
    public void setDeliveryTime(LocalDateTime deliveryTime) { this.deliveryTime = deliveryTime; }
    public BigDecimal getPlannedQtyKg() { return plannedQtyKg; }
    public void setPlannedQtyKg(BigDecimal plannedQtyKg) { this.plannedQtyKg = plannedQtyKg; }
    public Long getDeliveryPointId() { return deliveryPointId; }
    public void setDeliveryPointId(Long deliveryPointId) { this.deliveryPointId = deliveryPointId; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
}
