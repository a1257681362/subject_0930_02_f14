package com.evops.aqua.fishing.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 冷藏箱交接复称请求：净重与入库称重按在效容差规则比对，超阈值冻结该箱。
 * handoverRequestId 幂等——重复交接返回首次结果，不重复冻结。
 */
public class BoxHandoverRequest {

    @NotNull(message = "交接净重不能为空")
    private BigDecimal handoverWeightKg;

    @NotBlank(message = "交接人不能为空")
    private String handoverBy;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    public BigDecimal getHandoverWeightKg() { return handoverWeightKg; }
    public void setHandoverWeightKg(BigDecimal handoverWeightKg) { this.handoverWeightKg = handoverWeightKg; }
    public String getHandoverBy() { return handoverBy; }
    public void setHandoverBy(String handoverBy) { this.handoverBy = handoverBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
