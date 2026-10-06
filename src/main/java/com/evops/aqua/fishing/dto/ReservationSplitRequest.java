package com.evops.aqua.fishing.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 预约二次拆分请求：把一条尚未锁定的预约按规格执行段与收货点进一步拆分。
 * 第一段由母预约承接，其余生成子预约；各段计划量合计必须等于母预约剩余计划量，
 * 金额按原订单行单价分劈，拆分前后金额合计不变。
 * 外部海水养殖管理依赖无响应时直接失败——不写任何拆分结果，不假装成功。
 */
public class ReservationSplitRequest {

    @NotEmpty(message = "拆分明细不能为空")
    @Valid
    private List<ReservationSplitPart> parts;

    @NotBlank(message = "操作人不能为空")
    private String operator;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    public List<ReservationSplitPart> getParts() { return parts; }
    public void setParts(List<ReservationSplitPart> parts) { this.parts = parts; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
