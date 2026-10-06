package com.evops.aqua.fishing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户订单行金额口径：单价/行金额在订单创建时落账冻结。
 * 按规格和收货点的二次拆分只拆分预约（执行口径），预约永远挂靠同一订单行，
 * 拆前拆后同订单行各预约分劈金额（plannedAmount）合计始终等于本行 lineAmount。
 */
@TableName("t_fishing_order_line")
public class FishingOrderLine extends BaseEntity {

    private Integer lineNo;
    private Long orderId;
    private String specCode;
    private BigDecimal weightMinG;
    private BigDecimal weightMaxG;
    private LocalDateTime deliveryTime;
    private BigDecimal plannedQtyKg;
    private Long deliveryPointId;
    private String deliveryPointName;
    private BigDecimal unitPrice;
    private BigDecimal lineAmount;
    private String currency;
    private String requestId;

    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
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
    public String getDeliveryPointName() { return deliveryPointName; }
    public void setDeliveryPointName(String deliveryPointName) { this.deliveryPointName = deliveryPointName; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getLineAmount() { return lineAmount; }
    public void setLineAmount(BigDecimal lineAmount) { this.lineAmount = lineAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
