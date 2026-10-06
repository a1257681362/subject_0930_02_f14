package com.evops.aqua.fishing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 冷藏箱交接净重容差规则版本：差异同时受绝对公斤数（maxDiffKg）与
 * 百分比（maxDiffPct，按入库称重计）约束，任一超出即冻结对应冷藏箱。
 * 新版本发布后旧版本停用；交接冻结当时在效版本。
 */
@TableName("t_fishing_box_rule")
public class FishingBoxRule extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    private String ruleNo;
    private Integer versionNo;
    private BigDecimal maxDiffKg;
    private BigDecimal maxDiffPct;
    private String status;
    private String publishedBy;
    private LocalDateTime publishedTime;
    private String requestId;
    private String remark;

    public String getRuleNo() { return ruleNo; }
    public void setRuleNo(String ruleNo) { this.ruleNo = ruleNo; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public BigDecimal getMaxDiffKg() { return maxDiffKg; }
    public void setMaxDiffKg(BigDecimal maxDiffKg) { this.maxDiffKg = maxDiffKg; }
    public BigDecimal getMaxDiffPct() { return maxDiffPct; }
    public void setMaxDiffPct(BigDecimal maxDiffPct) { this.maxDiffPct = maxDiffPct; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPublishedBy() { return publishedBy; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }
    public LocalDateTime getPublishedTime() { return publishedTime; }
    public void setPublishedTime(LocalDateTime publishedTime) { this.publishedTime = publishedTime; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
