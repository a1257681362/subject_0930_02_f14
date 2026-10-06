package com.evops.aqua.fishing.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 收货点替换审批：申请后停留 PENDING；审批通过（APPROVED）才真正更新预约收货点
 * 并通知客户；驳回（REJECTED）不生效。requestId 幂等——重复申请返回首次单据。
 */
@TableName("t_fishing_point_change")
public class FishingPointChange extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    private String changeNo;
    private Long reservationId;
    private Long orderId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long oldPointId;
    private String oldPointName;
    private Long newPointId;
    private String newPointName;
    private String reason;
    private String status;
    private String appliedBy;
    private LocalDateTime appliedTime;
    private String reviewedBy;
    private LocalDateTime reviewTime;
    private String reviewRemark;
    private String requestId;

    public String getChangeNo() { return changeNo; }
    public void setChangeNo(String changeNo) { this.changeNo = changeNo; }
    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getOldPointId() { return oldPointId; }
    public void setOldPointId(Long oldPointId) { this.oldPointId = oldPointId; }
    public String getOldPointName() { return oldPointName; }
    public void setOldPointName(String oldPointName) { this.oldPointName = oldPointName; }
    public Long getNewPointId() { return newPointId; }
    public void setNewPointId(Long newPointId) { this.newPointId = newPointId; }
    public String getNewPointName() { return newPointName; }
    public void setNewPointName(String newPointName) { this.newPointName = newPointName; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAppliedBy() { return appliedBy; }
    public void setAppliedBy(String appliedBy) { this.appliedBy = appliedBy; }
    public LocalDateTime getAppliedTime() { return appliedTime; }
    public void setAppliedTime(LocalDateTime appliedTime) { this.appliedTime = appliedTime; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewTime() { return reviewTime; }
    public void setReviewTime(LocalDateTime reviewTime) { this.reviewTime = reviewTime; }
    public String getReviewRemark() { return reviewRemark; }
    public void setReviewRemark(String reviewRemark) { this.reviewRemark = reviewRemark; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
