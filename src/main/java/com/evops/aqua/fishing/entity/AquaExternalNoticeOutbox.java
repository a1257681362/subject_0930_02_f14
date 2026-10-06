package com.evops.aqua.fishing.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 外部通知发件箱：需要通知外部系统（海水养殖管理）的业务事件在本地事务内只落 PENDING。
 * (systemCode,bizType,bizId) 唯一——同一业务事件最多一条，恢复补发或重试绝不重复通知。
 * 状态 PENDING（待发送）/ SENT（已送达）/ FAILED（尝试失败，仍可继续补发）。
 */
@TableName("t_aqua_external_notice_outbox")
public class AquaExternalNoticeOutbox extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";

    public static final String BIZ_SPLIT_NOTICE = "ORDER_SPLIT";

    private String systemCode;
    private String bizType;
    private Long bizId;
    private String refNo;
    private String payload;
    private String status;
    private Integer sendCount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime sentTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lastError;
    private String requestId;

    public String getSystemCode() { return systemCode; }
    public void setSystemCode(String systemCode) { this.systemCode = systemCode; }
    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }
    public Long getBizId() { return bizId; }
    public void setBizId(Long bizId) { this.bizId = bizId; }
    public String getRefNo() { return refNo; }
    public void setRefNo(String refNo) { this.refNo = refNo; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getSendCount() { return sendCount; }
    public void setSendCount(Integer sendCount) { this.sendCount = sendCount; }
    public LocalDateTime getSentTime() { return sentTime; }
    public void setSentTime(LocalDateTime sentTime) { this.sentTime = sentTime; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
