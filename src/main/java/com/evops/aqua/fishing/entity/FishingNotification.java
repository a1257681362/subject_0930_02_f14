package com.evops.aqua.fishing.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 客户通知：临时换车、收货点替换审批通过等事件触发。
 * 按业务类型（bizType）+ 来源单据唯一：换车按 changeId、换点按 pointChangeId，
 * 同一业务事件只发一条。状态 SENT（已发送）/ ACKED（客户已签收）；重复签收不覆盖首次回执。
 */
@TableName("t_fishing_notification")
public class FishingNotification extends BaseEntity {

    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_ACKED = "ACKED";

    public static final String BIZ_VEHICLE_CHANGE = "VEHICLE_CHANGE";
    public static final String BIZ_POINT_CHANGE = "POINT_CHANGE";

    private String noticeNo;
    private Long orderId;
    private Long reservationId;
    private String customerName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long changeId;
    private String bizType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long pointChangeId;
    private String message;
    private String status;
    private String ackBy;
    private LocalDateTime ackTime;
    private String ackRemark;
    private String requestId;

    public String getNoticeNo() { return noticeNo; }
    public void setNoticeNo(String noticeNo) { this.noticeNo = noticeNo; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public Long getChangeId() { return changeId; }
    public void setChangeId(Long changeId) { this.changeId = changeId; }
    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }
    public Long getPointChangeId() { return pointChangeId; }
    public void setPointChangeId(Long pointChangeId) { this.pointChangeId = pointChangeId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAckBy() { return ackBy; }
    public void setAckBy(String ackBy) { this.ackBy = ackBy; }
    public LocalDateTime getAckTime() { return ackTime; }
    public void setAckTime(LocalDateTime ackTime) { this.ackTime = ackTime; }
    public String getAckRemark() { return ackRemark; }
    public void setAckRemark(String ackRemark) { this.ackRemark = ackRemark; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
