package com.evops.aqua.fishing.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 冷藏箱入库请求：捕捞后入冷藏库逐箱登记箱号、入库温度、称重结果。
 * 可关联现场分仓记录（loadId）。requestId 幂等，重复入库只形成一条箱记录。
 */
public class BoxStoreRequest {

    @NotBlank(message = "箱号不能为空")
    private String boxNo;

    private Long loadId;

    @NotNull(message = "入库温度不能为空")
    private BigDecimal inTempC;

    @NotNull(message = "入库称重结果不能为空")
    private BigDecimal inWeightKg;

    @NotBlank(message = "入库操作人不能为空")
    private String inBy;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    private String remark;

    public String getBoxNo() { return boxNo; }
    public void setBoxNo(String boxNo) { this.boxNo = boxNo; }
    public Long getLoadId() { return loadId; }
    public void setLoadId(Long loadId) { this.loadId = loadId; }
    public BigDecimal getInTempC() { return inTempC; }
    public void setInTempC(BigDecimal inTempC) { this.inTempC = inTempC; }
    public BigDecimal getInWeightKg() { return inWeightKg; }
    public void setInWeightKg(BigDecimal inWeightKg) { this.inWeightKg = inWeightKg; }
    public String getInBy() { return inBy; }
    public void setInBy(String inBy) { this.inBy = inBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
