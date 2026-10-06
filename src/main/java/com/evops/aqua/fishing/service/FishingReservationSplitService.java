package com.evops.aqua.fishing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.fishing.dto.ReservationSplitPart;
import com.evops.aqua.fishing.dto.ReservationSplitRequest;
import com.evops.aqua.fishing.entity.AquaExternalGatewayState;
import com.evops.aqua.fishing.entity.AquaExternalNoticeOutbox;
import com.evops.aqua.fishing.entity.FishingChangeEvidence;
import com.evops.aqua.fishing.entity.FishingDeliveryPoint;
import com.evops.aqua.fishing.entity.FishingOrder;
import com.evops.aqua.fishing.entity.FishingOrderLine;
import com.evops.aqua.fishing.entity.FishingReservation;
import com.evops.aqua.fishing.mapper.AquaExternalNoticeOutboxMapper;
import com.evops.aqua.fishing.mapper.FishingReservationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 预约二次拆分：按规格执行段和收货点把一条未执行预约进一步拆分。
 * 铁律：
 * 1) 拆单不改原订单金额口径——各段仍挂靠原订单行，按原行单价分劈金额，
 *    末段吸收舍入尾差，拆前拆后计划量与分劈金额合计恒等（服务端强校验）；
 * 2) 外部海水养殖管理依赖无响应时直接失败，先判依赖再落库，绝不本地假装成功；
 * 3) 拆分结果入发件箱通知外部，(system,bizType,bizId) 唯一 + 恢复补发，绝不重复通知；
 * 4) requestId 幂等——重试返回首次拆分结果，不新增子预约、不重复入箱。
 */
@Service
public class FishingReservationSplitService {

    private final FishingReservationMapper reservationMapper;
    private final AquaExternalNoticeOutboxMapper outboxMapper;
    private final FishingOrderService orderService;
    private final FishingDeliveryPointService pointService;
    private final AquaExternalGatewayService gatewayService;
    private final FishingEvidenceService evidenceService;
    private final ObjectMapper objectMapper;

    public FishingReservationSplitService(FishingReservationMapper reservationMapper,
                                          AquaExternalNoticeOutboxMapper outboxMapper,
                                          FishingOrderService orderService,
                                          FishingDeliveryPointService pointService,
                                          AquaExternalGatewayService gatewayService,
                                          FishingEvidenceService evidenceService,
                                          ObjectMapper objectMapper) {
        this.reservationMapper = reservationMapper;
        this.outboxMapper = outboxMapper;
        this.orderService = orderService;
        this.pointService = pointService;
        this.gatewayService = gatewayService;
        this.evidenceService = evidenceService;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<FishingReservation> split(Long id, ReservationSplitRequest request) {
        // 幂等优先：同 requestId 重试直接返回首次拆分的全部预约
        FishingReservation idempotentChild = reservationMapper.selectOne(new QueryWrapper<FishingReservation>()
                .eq("request_id", childRequestId(request.getRequestId(), 2)).last("limit 1"));
        if (idempotentChild != null) {
            return familyOf(idempotentChild.getSourceReservationId());
        }

        // 依赖闸：外部海水养殖管理无响应时，订单拆分必须失败——放在任何写操作之前
        gatewayService.requireAvailable(AquaExternalGatewayState.SYSTEM_MARICULTURE, "订单拆分");

        FishingReservation mother = reservationMapper.selectById(id);
        if (mother == null) {
            throw new IllegalArgumentException("捕捞预约不存在，id=" + id);
        }
        if (!FishingReservation.STATUS_DRAFT.equals(mother.getStatus())) {
            throw new IllegalArgumentException("仅草稿（未锁定）预约可拆分，当前状态：" + mother.getStatus());
        }
        Long childCount = reservationMapper.selectCount(new QueryWrapper<FishingReservation>()
                .eq("source_reservation_id", mother.getId()));
        if (childCount != null && childCount > 0) {
            throw new IllegalArgumentException("预约" + mother.getResvNo()
                    + "已二次拆分，不可重复拆分；如需调整请针对子预约操作");
        }
        List<ReservationSplitPart> parts = request.getParts();
        if (parts == null || parts.size() < 2) {
            throw new IllegalArgumentException("二次拆分至少要拆成两段");
        }
        FishingOrderLine line = orderService.orderLineOf(mother.getOrderLineId());
        BigDecimal qtySum = BigDecimal.ZERO;
        for (ReservationSplitPart part : parts) {
            if (part.getPlannedQtyKg() == null || part.getPlannedQtyKg().signum() <= 0) {
                throw new IllegalArgumentException("每段拆分计划量必须大于0");
            }
            qtySum = qtySum.add(part.getPlannedQtyKg());
        }
        if (qtySum.compareTo(mother.getPlannedQtyKg()) != 0) {
            throw new IllegalArgumentException("各段计划量合计" + qtySum + "kg必须等于预约原计划量"
                    + mother.getPlannedQtyKg() + "kg（拆单不能改变原订单数量/金额口径）");
        }

        FishingOrder order = orderService.getOrThrow(mother.getOrderId());
        BigDecimal unitPrice = line.getUnitPrice();

        // 第一段由母预约承接；金额先按 量×原单价 分劈，末段吸收尾差保证合计等于行金额分劈基数
        List<FishingReservation> family = new ArrayList<>();
        List<BigDecimal> allocatedAmounts = allocateAmounts(parts, unitPrice, mother.getPlannedAmount());

        // 母预约承接第一段
        FishingReservation before = copyOf(mother);
        ReservationSplitPart firstPart = parts.get(0);
        FishingDeliveryPoint firstPoint = resolvePoint(firstPart, mother);
        mother.setPlannedQtyKg(parts.get(0).getPlannedQtyKg());
        mother.setPlannedAmount(allocatedAmounts.get(0));
        if (firstPoint != null) {
            mother.setDeliveryPointId(firstPoint.getId());
            mother.setDeliveryPointName(firstPoint.getPointName());
        }
        reservationMapper.updateById(mother);
        family.add(mother);

        Long childLineId = line.getId();
        // 其余段生成子预约
        for (int i = 1; i < parts.size(); i++) {
            ReservationSplitPart part = parts.get(i);
            FishingDeliveryPoint point = resolvePoint(part, mother);
            FishingReservation child = new FishingReservation();
            child.setResvNo(mother.getResvNo() + "-S" + i);
            child.setOrderId(mother.getOrderId());
            child.setOrderLineId(childLineId);
            child.setSourceReservationId(mother.getId());
            child.setSpecCode(mother.getSpecCode());
            child.setWeightMinG(mother.getWeightMinG());
            child.setWeightMaxG(mother.getWeightMaxG());
            child.setDeliveryTime(mother.getDeliveryTime());
            child.setPlannedQtyKg(part.getPlannedQtyKg());
            child.setActualQtyKg(BigDecimal.ZERO);
            child.setVehicleNo(mother.getVehicleNo());
            if (point != null) {
                child.setDeliveryPointId(point.getId());
                child.setDeliveryPointName(point.getPointName());
            } else {
                child.setDeliveryPointId(mother.getDeliveryPointId());
                child.setDeliveryPointName(mother.getDeliveryPointName());
            }
            child.setPlannedAmount(allocatedAmounts.get(i));
            child.setStatus(FishingReservation.STATUS_DRAFT);
            child.setRequestId(childRequestId(request.getRequestId(), i + 1));
            reservationMapper.insert(child);
            family.add(child);
            evidenceService.record(FishingChangeEvidence.ENTITY_RESERVATION, child.getId(),
                    FishingChangeEvidence.ACTION_RESV_SPLIT, child.getRequestId(), request.getOperator(),
                    null, child, "预约" + mother.getResvNo() + "二次拆分出子预约" + child.getResvNo()
                            + "：规格" + child.getSpecCode() + "，计划" + child.getPlannedQtyKg()
                            + "kg，分劈金额" + child.getPlannedAmount() + "（原行单价" + unitPrice + "）"
                            + (point == null ? "" : "，收货点" + point.getPointName()));
        }
        evidenceService.record(FishingChangeEvidence.ENTITY_RESERVATION, mother.getId(),
                FishingChangeEvidence.ACTION_RESV_SPLIT, request.getRequestId(), request.getOperator(),
                before, mother, "预约" + mother.getResvNo() + "二次拆分为" + parts.size()
                        + "段，母预约承接首段计划" + mother.getPlannedQtyKg() + "kg/金额"
                        + mother.getPlannedAmount() + "；拆前拆后金额合计不变");

        // 金额守恒强校验：同订单行全部预约合计必须仍等于原行金额
        orderService.requireAmountConserved(line.getId());

        // 拆分结果通知外部海水养殖管理：入发件箱（bizId 取首个新子预约id，天然区分各次拆分且稳定可幂等）
        FishingReservation firstNewChild = family.get(1);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderNo", order.getOrderNo());
        payload.put("sourceResvNo", mother.getResvNo());
        payload.put("partCount", parts.size());
        List<Map<String, Object>> partViews = new ArrayList<>();
        for (FishingReservation r : family) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("resvNo", r.getResvNo());
            v.put("specCode", r.getSpecCode());
            v.put("plannedQtyKg", r.getPlannedQtyKg());
            v.put("deliveryPointName", r.getDeliveryPointName());
            partViews.add(v);
        }
        payload.put("parts", partViews);
        gatewayService.enqueue(AquaExternalGatewayState.SYSTEM_MARICULTURE,
                AquaExternalNoticeOutbox.BIZ_SPLIT_NOTICE, firstNewChild.getId(),
                mother.getResvNo(), toJson(payload), request.getRequestId() + "#outbox", request.getOperator());
        return family;
    }

    public List<FishingReservation> familyOf(Long motherId) {
        List<FishingReservation> family = new ArrayList<>();
        FishingReservation mother = reservationMapper.selectById(motherId);
        if (mother != null) {
            family.add(mother);
            family.addAll(reservationMapper.selectList(new QueryWrapper<FishingReservation>()
                    .eq("source_reservation_id", motherId).orderByAsc("id")));
        }
        return family;
    }

    private FishingDeliveryPoint resolvePoint(ReservationSplitPart part, FishingReservation mother) {
        if (part.getDeliveryPointId() != null) {
            return pointService.requireActive(part.getDeliveryPointId());
        }
        return null;
    }

    /**
     * 金额分劈：前 n-1 段 = 量×原单价（2位四舍五入），末段 = 母预约原金额 - 已分劈合计，
     * 吸收尾差，保证合计严格等于母预约金额（即原订单金额口径不变）。
     */
    private List<BigDecimal> allocateAmounts(List<ReservationSplitPart> parts,
                                             BigDecimal unitPrice, BigDecimal motherAmount) {
        List<BigDecimal> amounts = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < parts.size(); i++) {
            if (i < parts.size() - 1) {
                BigDecimal amount = parts.get(i).getPlannedQtyKg().multiply(unitPrice)
                        .setScale(2, BigDecimal.ROUND_HALF_UP);
                amounts.add(amount);
                allocated = allocated.add(amount);
            } else {
                amounts.add(motherAmount.subtract(allocated).setScale(2, BigDecimal.ROUND_HALF_UP));
            }
        }
        return amounts;
    }

    private String childRequestId(String splitRequestId, int seq) {
        return splitRequestId + "#S" + seq;
    }

    private FishingReservation copyOf(FishingReservation reservation) {
        FishingReservation copy = new FishingReservation();
        org.springframework.beans.BeanUtils.copyProperties(reservation, copy);
        return copy;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }
}
