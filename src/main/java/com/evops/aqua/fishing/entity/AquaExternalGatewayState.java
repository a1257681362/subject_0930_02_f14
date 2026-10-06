package com.evops.aqua.fishing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 外部依赖可用性状态（海水养殖管理等）。无响应/超时时由熔断或运维置为不可用，
 * 依赖该系统的订单拆分等写操作必须失败返回——本地不得假装成功。
 * 恢复置位后由发件箱补发挂起通知。
 */
@TableName("t_aqua_external_gateway_state")
public class AquaExternalGatewayState extends BaseEntity {

    public static final String SYSTEM_MARICULTURE = "MARICULTURE_MGMT";

    private String systemCode;
    private Boolean available;
    private String reason;
    private String updatedBy;
    private LocalDateTime updatedTime;

    public String getSystemCode() { return systemCode; }
    public void setSystemCode(String systemCode) { this.systemCode = systemCode; }
    public Boolean getAvailable() { return available; }
    public void setAvailable(Boolean available) { this.available = available; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }
}
