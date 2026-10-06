package com.evops.aqua.fishing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 收货点档案：订单可按收货点拆分交付目的地；停用（DISABLED）后不得作为新收货点。
 */
@TableName("t_fishing_delivery_point")
public class FishingDeliveryPoint extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    private String pointNo;
    private String pointName;
    private String address;
    private String status;
    private String operator;
    private String requestId;
    private String remark;

    public String getPointNo() { return pointNo; }
    public void setPointNo(String pointNo) { this.pointNo = pointNo; }
    public String getPointName() { return pointName; }
    public void setPointName(String pointName) { this.pointName = pointName; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
