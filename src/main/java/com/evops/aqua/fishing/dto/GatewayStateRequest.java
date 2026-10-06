package com.evops.aqua.fishing.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 外部依赖可用性置位（运维/熔断）：无响应置 available=false，恢复置 true。
 */
public class GatewayStateRequest {

    @NotNull(message = "可用性状态不能为空")
    private Boolean available;

    private String reason;

    @NotBlank(message = "操作人不能为空")
    private String updatedBy;

    public Boolean getAvailable() { return available; }
    public void setAvailable(Boolean available) { this.available = available; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
