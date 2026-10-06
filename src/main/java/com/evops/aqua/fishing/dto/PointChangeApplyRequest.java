package com.evops.aqua.fishing.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 收货点替换申请：只提交申请（PENDING），审批通过前预约收货点不变。
 */
public class PointChangeApplyRequest {

    @NotNull(message = "新收货点不能为空")
    private Long newPointId;

    @NotBlank(message = "替换原因不能为空")
    private String reason;

    @NotBlank(message = "申请人不能为空")
    private String appliedBy;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    public Long getNewPointId() { return newPointId; }
    public void setNewPointId(Long newPointId) { this.newPointId = newPointId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getAppliedBy() { return appliedBy; }
    public void setAppliedBy(String appliedBy) { this.appliedBy = appliedBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
