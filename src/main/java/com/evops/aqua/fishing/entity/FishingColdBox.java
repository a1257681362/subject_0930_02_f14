package com.evops.aqua.fishing.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 冷藏箱：捕捞后入冷藏库逐箱登记，记录入库温度、箱号、称重结果（毛/净重）。
 * 交接时复称净重，与入库称重按在效容差规则比对：绝对差或差异率任一超阈值即 FROZEN，
 * 冻结箱不得交付。状态 IN_STORE（在库）/ HANDED_OVER（已交接）/ FROZEN（已冻结）。
 * 入库 requestId 与交接 handoverRequestId 各自幂等，重复交接返回首次结果、不重复冻结。
 */
@TableName("t_fishing_cold_box")
public class FishingColdBox extends BaseEntity {

    public static final String STATUS_IN_STORE = "IN_STORE";
    public static final String STATUS_HANDED_OVER = "HANDED_OVER";
    public static final String STATUS_FROZEN = "FROZEN";

    private String boxNo;
    private Long reservationId;
    private Long orderId;
    private Long orderLineId;
    private Long loadId;
    private String specCode;
    private String status;
    private BigDecimal inTempC;
    private BigDecimal inWeightKg;
    private LocalDateTime inTime;
    private String inBy;
    private BigDecimal handoverWeightKg;
    private BigDecimal diffKg;
    private BigDecimal diffPct;
    private Long boxRuleId;
    private String frozenReason;
    private String handoverBy;
    private LocalDateTime handoverTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String handoverRequestId;
    private String requestId;
    private String remark;

    public String getBoxNo() { return boxNo; }
    public void setBoxNo(String boxNo) { this.boxNo = boxNo; }
    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getOrderLineId() { return orderLineId; }
    public void setOrderLineId(Long orderLineId) { this.orderLineId = orderLineId; }
    public Long getLoadId() { return loadId; }
    public void setLoadId(Long loadId) { this.loadId = loadId; }
    public String getSpecCode() { return specCode; }
    public void setSpecCode(String specCode) { this.specCode = specCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getInTempC() { return inTempC; }
    public void setInTempC(BigDecimal inTempC) { this.inTempC = inTempC; }
    public BigDecimal getInWeightKg() { return inWeightKg; }
    public void setInWeightKg(BigDecimal inWeightKg) { this.inWeightKg = inWeightKg; }
    public LocalDateTime getInTime() { return inTime; }
    public void setInTime(LocalDateTime inTime) { this.inTime = inTime; }
    public String getInBy() { return inBy; }
    public void setInBy(String inBy) { this.inBy = inBy; }
    public BigDecimal getHandoverWeightKg() { return handoverWeightKg; }
    public void setHandoverWeightKg(BigDecimal handoverWeightKg) { this.handoverWeightKg = handoverWeightKg; }
    public BigDecimal getDiffKg() { return diffKg; }
    public void setDiffKg(BigDecimal diffKg) { this.diffKg = diffKg; }
    public BigDecimal getDiffPct() { return diffPct; }
    public void setDiffPct(BigDecimal diffPct) { this.diffPct = diffPct; }
    public Long getBoxRuleId() { return boxRuleId; }
    public void setBoxRuleId(Long boxRuleId) { this.boxRuleId = boxRuleId; }
    public String getFrozenReason() { return frozenReason; }
    public void setFrozenReason(String frozenReason) { this.frozenReason = frozenReason; }
    public String getHandoverBy() { return handoverBy; }
    public void setHandoverBy(String handoverBy) { this.handoverBy = handoverBy; }
    public LocalDateTime getHandoverTime() { return handoverTime; }
    public void setHandoverTime(LocalDateTime handoverTime) { this.handoverTime = handoverTime; }
    public String getHandoverRequestId() { return handoverRequestId; }
    public void setHandoverRequestId(String handoverRequestId) { this.handoverRequestId = handoverRequestId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
