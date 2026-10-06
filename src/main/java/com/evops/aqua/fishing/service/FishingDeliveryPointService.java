package com.evops.aqua.fishing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.fishing.dto.PointRegisterRequest;
import com.evops.aqua.fishing.entity.FishingChangeEvidence;
import com.evops.aqua.fishing.entity.FishingDeliveryPoint;
import com.evops.aqua.fishing.mapper.FishingDeliveryPointMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 收货点档案：订单与预约的交付目的地，订单可按收货点拆分。
 * requestId 幂等登记；停用后不得作为新收货点（保留以满足历史单据展示）。
 */
@Service
public class FishingDeliveryPointService {

    private final FishingDeliveryPointMapper pointMapper;
    private final FishingEvidenceService evidenceService;

    public FishingDeliveryPointService(FishingDeliveryPointMapper pointMapper,
                                       FishingEvidenceService evidenceService) {
        this.pointMapper = pointMapper;
        this.evidenceService = evidenceService;
    }

    @Transactional(rollbackFor = Exception.class)
    public FishingDeliveryPoint register(PointRegisterRequest request) {
        FishingDeliveryPoint duplicated = pointMapper.selectOne(new QueryWrapper<FishingDeliveryPoint>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (duplicated != null) {
            return duplicated;
        }
        FishingDeliveryPoint sameNo = pointMapper.selectOne(new QueryWrapper<FishingDeliveryPoint>()
                .eq("point_no", request.getPointNo()).last("limit 1"));
        if (sameNo != null) {
            throw new IllegalArgumentException("收货点编号" + request.getPointNo() + "已存在，请更换编号");
        }
        FishingDeliveryPoint point = new FishingDeliveryPoint();
        point.setPointNo(request.getPointNo());
        point.setPointName(request.getPointName());
        point.setAddress(request.getAddress());
        point.setStatus(FishingDeliveryPoint.STATUS_ACTIVE);
        point.setOperator(request.getOperator());
        point.setRequestId(request.getRequestId());
        point.setRemark(request.getRemark());
        pointMapper.insert(point);
        evidenceService.record(FishingChangeEvidence.ENTITY_DELIVERY_POINT, point.getId(),
                FishingChangeEvidence.ACTION_POINT_REGISTER, request.getRequestId(), request.getOperator(),
                null, point, "登记收货点" + point.getPointNo() + "（" + point.getPointName() + "）");
        return point;
    }

    /** 停用收货点：已停用重复停用幂等返回。停用后不得再被订单/拆单/换点引用。 */
    @Transactional(rollbackFor = Exception.class)
    public FishingDeliveryPoint disable(Long id, String operator) {
        FishingDeliveryPoint point = getOrThrow(id);
        if (FishingDeliveryPoint.STATUS_DISABLED.equals(point.getStatus())) {
            return point;
        }
        FishingDeliveryPoint before = copyOf(point);
        point.setStatus(FishingDeliveryPoint.STATUS_DISABLED);
        pointMapper.updateById(point);
        evidenceService.record(FishingChangeEvidence.ENTITY_DELIVERY_POINT, point.getId(),
                FishingChangeEvidence.ACTION_POINT_DISABLE, null, operator,
                before, point, "收货点" + point.getPointNo() + "停用");
        return point;
    }

    /** 取在效收货点；不存在或停用均拒绝（停用点不得作为新收货点）。 */
    public FishingDeliveryPoint requireActive(Long id) {
        FishingDeliveryPoint point = pointMapper.selectById(id);
        if (point == null) {
            throw new IllegalArgumentException("收货点不存在，id=" + id);
        }
        if (!FishingDeliveryPoint.STATUS_ACTIVE.equals(point.getStatus())) {
            throw new IllegalArgumentException("收货点" + point.getPointNo() + "已停用，不能作为交付收货点");
        }
        return point;
    }

    public FishingDeliveryPoint getOrThrow(Long id) {
        FishingDeliveryPoint point = pointMapper.selectById(id);
        if (point == null) {
            throw new IllegalArgumentException("收货点不存在，id=" + id);
        }
        return point;
    }

    public List<FishingDeliveryPoint> list(String status) {
        QueryWrapper<FishingDeliveryPoint> wrapper = new QueryWrapper<>();
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return pointMapper.selectList(wrapper);
    }

    private FishingDeliveryPoint copyOf(FishingDeliveryPoint point) {
        FishingDeliveryPoint copy = new FishingDeliveryPoint();
        org.springframework.beans.BeanUtils.copyProperties(point, copy);
        return copy;
    }
}
