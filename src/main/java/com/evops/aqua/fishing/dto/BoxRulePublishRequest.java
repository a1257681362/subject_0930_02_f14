package com.evops.aqua.fishing.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 冷藏箱交接净重容差规则发布请求。
 * 差异同时受绝对公斤数与百分比（按入库称重）约束，任一超出即冻结该箱。
 */
public class BoxRulePublishRequest {

    @NotNull(message = "允许净重差异公斤数不能为空")
    private BigDecimal maxDiffKg;

    @NotNull(message = "允许净重差异百分比不能为空")
    private BigDecimal maxDiffPct;

    @NotBlank(message = "发布人不能为空")
    private String publishedBy;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    private String remark;

    public BigDecimal getMaxDiffKg() { return maxDiffKg; }
    public void setMaxDiffKg(BigDecimal maxDiffKg) { this.maxDiffKg = maxDiffKg; }
    public BigDecimal getMaxDiffPct() { return maxDiffPct; }
    public void setMaxDiffPct(BigDecimal maxDiffPct) { this.maxDiffPct = maxDiffPct; }
    public String getPublishedBy() { return publishedBy; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
