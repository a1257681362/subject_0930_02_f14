package com.evops.aqua.fishing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.fishing.dto.BoxHandoverRequest;
import com.evops.aqua.fishing.dto.BoxStoreRequest;
import com.evops.aqua.fishing.entity.FishingBoxRule;
import com.evops.aqua.fishing.entity.FishingChangeEvidence;
import com.evops.aqua.fishing.entity.FishingColdBox;
import com.evops.aqua.fishing.entity.FishingLoad;
import com.evops.aqua.fishing.entity.FishingReservation;
import com.evops.aqua.fishing.mapper.FishingColdBoxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 冷藏箱履约：捕捞后入冷藏库逐箱记录入库温度、箱号、称重结果；
 * 交接时复称净重，与入库称重按在效容差规则（绝对公斤数 + 百分比）比对，
 * 任一超出即冻结该箱（FROZEN），冻结箱不得交付（预约完成时拦截）。
 * 入库与交接各自 requestId 幂等——补录/重复交接只形成一条可用结果、不重复冻结。
 */
@Service
public class FishingColdBoxService {

    private final FishingColdBoxMapper boxMapper;
    private final FishingBoxRuleService ruleService;
    private final FishingReservationService reservationService;
    private final FishingEvidenceService evidenceService;

    public FishingColdBoxService(FishingColdBoxMapper boxMapper,
                                 FishingBoxRuleService ruleService,
                                 FishingReservationService reservationService,
                                 FishingEvidenceService evidenceService) {
        this.boxMapper = boxMapper;
        this.ruleService = ruleService;
        this.reservationService = reservationService;
        this.evidenceService = evidenceService;
    }

    /**
     * 冷藏箱入库：登记箱号、入库温度、称重结果。仅已锁定（已捕捞分仓）预约可入库。
     * requestId 幂等；boxNo 全局唯一。
     */
    @Transactional(rollbackFor = Exception.class)
    public FishingColdBox store(Long reservationId, BoxStoreRequest request) {
        FishingColdBox duplicated = boxMapper.selectOne(new QueryWrapper<FishingColdBox>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (duplicated != null) {
            return duplicated;
        }
        FishingReservation reservation = reservationService.getOrThrow(reservationId);
        if (FishingReservation.STATUS_DRAFT.equals(reservation.getStatus())) {
            throw new IllegalArgumentException("预约尚未锁定捕捞，不能办理冷藏入库");
        }
        if (FishingReservation.STATUS_COMPLETED.equals(reservation.getStatus())) {
            throw new IllegalArgumentException("预约已完成交付，不能再办理冷藏入库");
        }
        if (request.getInWeightKg() == null || request.getInWeightKg().signum() <= 0) {
            throw new IllegalArgumentException("入库称重结果必须大于0");
        }
        if (request.getInTempC() == null) {
            throw new IllegalArgumentException("入库温度必须登记");
        }
        FishingColdBox sameNo = boxMapper.selectOne(new QueryWrapper<FishingColdBox>()
                .eq("box_no", request.getBoxNo()).last("limit 1"));
        if (sameNo != null) {
            throw new IllegalArgumentException("箱号" + request.getBoxNo() + "已存在，请更换箱号");
        }
        FishingLoad load = null;
        if (request.getLoadId() != null) {
            List<FishingLoad> loads = reservationService.loadsOf(reservationId);
            for (FishingLoad l : loads) {
                if (l.getId().equals(request.getLoadId())) {
                    load = l;
                    break;
                }
            }
            if (load == null) {
                throw new IllegalArgumentException("分仓记录id=" + request.getLoadId()
                        + "不属于预约" + reservation.getResvNo());
            }
        }

        FishingColdBox box = new FishingColdBox();
        box.setBoxNo(request.getBoxNo());
        box.setReservationId(reservationId);
        box.setOrderId(reservation.getOrderId());
        box.setOrderLineId(reservation.getOrderLineId());
        box.setLoadId(load == null ? null : load.getId());
        box.setSpecCode(reservation.getSpecCode());
        box.setStatus(FishingColdBox.STATUS_IN_STORE);
        box.setInTempC(request.getInTempC());
        box.setInWeightKg(request.getInWeightKg());
        box.setInTime(LocalDateTime.now());
        box.setInBy(request.getInBy());
        box.setRequestId(request.getRequestId());
        box.setRemark(request.getRemark());
        boxMapper.insert(box);
        evidenceService.record(FishingChangeEvidence.ENTITY_COLD_BOX, box.getId(),
                FishingChangeEvidence.ACTION_BOX_STORE, request.getRequestId(), request.getInBy(),
                null, box, "捕捞后冷藏入库：箱号" + box.getBoxNo() + "，入库温度" + box.getInTempC()
                        + "℃，称重" + box.getInWeightKg() + "kg，预约" + reservation.getResvNo());
        return box;
    }

    /**
     * 冷藏箱交接复称：净重与入库称重按在效容差规则比对。
     * 差异绝对公斤数或百分比任一超阈值 → 冻结该箱，记录差异与冻结原因，不得交付；
     * 未超 → HANDED_OVER。交接幂等：同 handoverRequestId/已交接已冻结的箱重复交接返回首次结果。
     */
    @Transactional(rollbackFor = Exception.class)
    public FishingColdBox handover(Long boxId, BoxHandoverRequest request) {
        FishingColdBox box = boxMapper.selectById(boxId);
        if (box == null) {
            throw new IllegalArgumentException("冷藏箱不存在，id=" + boxId);
        }
        // 交接幂等：已产生交接结论（已交接/已冻结）直接返回首次结果，重复复称不重复冻结
        if (!FishingColdBox.STATUS_IN_STORE.equals(box.getStatus())
                || box.getHandoverRequestId() != null) {
            return box;
        }
        if (request.getHandoverWeightKg() == null || request.getHandoverWeightKg().signum() <= 0) {
            throw new IllegalArgumentException("交接净重必须大于0");
        }
        FishingBoxRule rule = ruleService.activeRule();
        BigDecimal diff = request.getHandoverWeightKg().subtract(box.getInWeightKg());
        BigDecimal diffAbs = diff.abs();
        BigDecimal diffPct = BigDecimal.ZERO;
        if (box.getInWeightKg().signum() > 0) {
            diffPct = diffAbs.multiply(new BigDecimal("100"))
                    .divide(box.getInWeightKg(), 4, BigDecimal.ROUND_HALF_UP);
        }
        box.setHandoverWeightKg(request.getHandoverWeightKg());
        box.setDiffKg(diffAbs);
        box.setDiffPct(diffPct);
        box.setBoxRuleId(rule.getId());
        box.setHandoverBy(request.getHandoverBy());
        box.setHandoverTime(LocalDateTime.now());
        box.setHandoverRequestId(request.getRequestId());

        boolean exceeds = ruleService.exceeds(rule, box.getInWeightKg(), diffAbs);
        FishingColdBox before = copyOf(box);
        if (exceeds) {
            box.setStatus(FishingColdBox.STATUS_FROZEN);
            String reason = "交接净重" + request.getHandoverWeightKg() + "kg与入库称重" + box.getInWeightKg()
                    + "kg差异" + diffAbs + "kg（差异率" + diffPct + "%），超过容差规则" + rule.getRuleNo()
                    + "（允许绝对" + rule.getMaxDiffKg() + "kg、差异率" + rule.getMaxDiffPct()
                    + "%），冻结该箱，不得交付";
            box.setFrozenReason(reason);
            boxMapper.updateById(box);
            evidenceService.record(FishingChangeEvidence.ENTITY_COLD_BOX, box.getId(),
                    FishingChangeEvidence.ACTION_BOX_FREEZE, request.getRequestId(), request.getHandoverBy(),
                    before, box, reason);
        } else {
            box.setStatus(FishingColdBox.STATUS_HANDED_OVER);
            boxMapper.updateById(box);
            evidenceService.record(FishingChangeEvidence.ENTITY_COLD_BOX, box.getId(),
                    FishingChangeEvidence.ACTION_BOX_HANDOVER, request.getRequestId(), request.getHandoverBy(),
                    before, box, "冷藏箱" + box.getBoxNo() + "交接复称" + request.getHandoverWeightKg()
                            + "kg，差异" + diffAbs + "kg（差异率" + diffPct + "%）在容差规则"
                            + rule.getRuleNo() + "内，正常交接");
        }
        return box;
    }

    public FishingColdBox getOrThrow(Long boxId) {
        FishingColdBox box = boxMapper.selectById(boxId);
        if (box == null) {
            throw new IllegalArgumentException("冷藏箱不存在，id=" + boxId);
        }
        return box;
    }

    public List<FishingColdBox> boxesOfReservation(Long reservationId) {
        return boxMapper.selectList(new QueryWrapper<FishingColdBox>()
                .eq("reservation_id", reservationId).orderByAsc("id"));
    }

    public List<FishingColdBox> boxesOfOrder(Long orderId) {
        return boxMapper.selectList(new QueryWrapper<FishingColdBox>()
                .eq("order_id", orderId).orderByAsc("id"));
    }

    private FishingColdBox copyOf(FishingColdBox box) {
        FishingColdBox copy = new FishingColdBox();
        org.springframework.beans.BeanUtils.copyProperties(box, copy);
        return copy;
    }
}
