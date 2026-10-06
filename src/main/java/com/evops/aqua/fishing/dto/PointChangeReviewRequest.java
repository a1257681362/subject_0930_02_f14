package com.evops.aqua.fishing.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 收货点替换审批：approved=true 批准（预约收货点更新并通知客户），false 驳回（不生效）。
 * 审批动作幂等——已审批单据重复审批返回首次结论，不重复通知。
 */
public class PointChangeReviewRequest {

    @NotNull(message = "审批结论不能为空")
    private Boolean approved;

    @NotBlank(message = "审批人不能为空")
    private String reviewedBy;

    private String reviewRemark;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    public Boolean getApproved() { return approved; }
    public void setApproved(Boolean approved) { this.approved = approved; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getReviewRemark() { return reviewRemark; }
    public void setReviewRemark(String reviewRemark) { this.reviewRemark = reviewRemark; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
