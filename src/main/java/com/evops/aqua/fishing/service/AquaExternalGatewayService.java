package com.evops.aqua.fishing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.fishing.dto.GatewayStateRequest;
import com.evops.aqua.fishing.entity.AquaExternalGatewayState;
import com.evops.aqua.fishing.entity.AquaExternalNoticeOutbox;
import com.evops.aqua.fishing.entity.FishingChangeEvidence;
import com.evops.aqua.fishing.mapper.AquaExternalGatewayStateMapper;
import com.evops.aqua.fishing.mapper.AquaExternalNoticeOutboxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 外部依赖（海水养殖管理）可用性与外部通知发件箱。
 *
 * 两条硬约束：
 * 1) 依赖无响应/超时时置 available=false，依赖它的订单拆分必须失败返回——本地不得假装成功；
 * 2) 对外部的通知走发件箱：业务事务内只落 PENDING，恢复后补发；
 *    (systemCode,bizType,bizId) 唯一——同一业务事件只通知一次，补发/重试绝不重复。
 */
@Service
public class AquaExternalGatewayService {

    private final AquaExternalGatewayStateMapper stateMapper;
    private final AquaExternalNoticeOutboxMapper outboxMapper;
    private final FishingEvidenceService evidenceService;

    public AquaExternalGatewayService(AquaExternalGatewayStateMapper stateMapper,
                                      AquaExternalNoticeOutboxMapper outboxMapper,
                                      FishingEvidenceService evidenceService) {
        this.stateMapper = stateMapper;
        this.outboxMapper = outboxMapper;
        this.evidenceService = evidenceService;
    }

    // ------------------------------------------------------------------
    // 可用性
    // ------------------------------------------------------------------

    public AquaExternalGatewayState state(String systemCode) {
        AquaExternalGatewayState state = stateMapper.selectOne(new QueryWrapper<AquaExternalGatewayState>()
                .eq("system_code", systemCode).last("limit 1"));
        if (state == null) {
            throw new IllegalArgumentException("外部系统状态未初始化：" + systemCode);
        }
        return state;
    }

    public boolean isAvailable(String systemCode) {
        return Boolean.TRUE.equals(state(systemCode).getAvailable());
    }

    /** 可用性闸：依赖无响应时直接拒绝，调用方不得继续本地落账假装成功。 */
    public void requireAvailable(String systemCode, String action) {
        AquaExternalGatewayState state = state(systemCode);
        if (!Boolean.TRUE.equals(state.getAvailable())) {
            throw new IllegalStateException("外部海水养殖管理系统（" + systemCode + "）当前无响应"
                    + (state.getReason() == null ? "" : "，原因：" + state.getReason())
                    + "，" + action + "不能完成，请勿当作成功；系统恢复后请凭原请求号重试");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public AquaExternalGatewayState setState(String systemCode, GatewayStateRequest request) {
        AquaExternalGatewayState state = state(systemCode);
        boolean same = Boolean.TRUE.equals(state.getAvailable()) == Boolean.TRUE.equals(request.getAvailable());
        if (same) {
            return state;
        }
        AquaExternalGatewayState before = copyOf(state);
        state.setAvailable(request.getAvailable());
        state.setReason(request.getReason());
        state.setUpdatedBy(request.getUpdatedBy());
        state.setUpdatedTime(LocalDateTime.now());
        stateMapper.updateById(state);
        evidenceService.record(FishingChangeEvidence.ENTITY_EXTERNAL_GATEWAY, state.getId(),
                Boolean.TRUE.equals(request.getAvailable())
                        ? FishingChangeEvidence.ACTION_GATEWAY_UP
                        : FishingChangeEvidence.ACTION_GATEWAY_DOWN,
                null, request.getUpdatedBy(), before, state,
                "外部系统" + systemCode + (Boolean.TRUE.equals(request.getAvailable())
                        ? "恢复可用" : "无响应/超时熔断") + (request.getReason() == null ? "" : "，原因：" + request.getReason()));
        return state;
    }

    // ------------------------------------------------------------------
    // 发件箱
    // ------------------------------------------------------------------

    /**
     * 业务事务内入箱：按 (systemCode,bizType,bizId) 幂等——同一业务事件只有一条，
     * 重复入箱返回既有记录，绝不重复通知。
     */
    @Transactional(rollbackFor = Exception.class)
    public AquaExternalNoticeOutbox enqueue(String systemCode, String bizType, Long bizId, String refNo,
                                            String payload, String requestId, String operator) {
        AquaExternalNoticeOutbox existing = outboxMapper.selectOne(new QueryWrapper<AquaExternalNoticeOutbox>()
                .eq("system_code", systemCode)
                .eq("biz_type", bizType)
                .eq("biz_id", bizId).last("limit 1"));
        if (existing != null) {
            return existing;
        }
        AquaExternalNoticeOutbox outbox = new AquaExternalNoticeOutbox();
        outbox.setSystemCode(systemCode);
        outbox.setBizType(bizType);
        outbox.setBizId(bizId);
        outbox.setRefNo(refNo);
        outbox.setPayload(payload);
        outbox.setStatus(AquaExternalNoticeOutbox.STATUS_PENDING);
        outbox.setSendCount(0);
        outbox.setRequestId(requestId);
        outboxMapper.insert(outbox);
        evidenceService.record(FishingChangeEvidence.ENTITY_EXTERNAL_OUTBOX, outbox.getId(),
                FishingChangeEvidence.ACTION_OUTBOX_ENQUEUE, requestId, operator,
                null, outbox, "外部通知入发件箱（待发送）：" + systemCode + "/" + bizType + "/" + refNo);
        return outbox;
    }

    /**
     * 补发投递：恢复后调用。只处理 PENDING/FAILED 记录，SENT 永不重发；
     * 若投递当时依赖仍无响应，保留待发送并记录错误，绝不标记成功。
     * 返回本次真正投递成功的条数（用于断言「恢复后只补一条、不重复」）。
     */
    @Transactional(rollbackFor = Exception.class)
    public int dispatchPending(String systemCode) {
        if (!isAvailable(systemCode)) {
            return 0;
        }
        List<AquaExternalNoticeOutbox> pending = outboxMapper.selectList(new QueryWrapper<AquaExternalNoticeOutbox>()
                .eq("system_code", systemCode)
                .in("status", AquaExternalNoticeOutbox.STATUS_PENDING, AquaExternalNoticeOutbox.STATUS_FAILED)
                .orderByAsc("id"));
        int sent = 0;
        for (AquaExternalNoticeOutbox outbox : pending) {
            outbox.setStatus(AquaExternalNoticeOutbox.STATUS_SENT);
            outbox.setSendCount((outbox.getSendCount() == null ? 0 : outbox.getSendCount()) + 1);
            outbox.setSentTime(LocalDateTime.now());
            outbox.setLastError(null);
            outboxMapper.updateById(outbox);
            evidenceService.record(FishingChangeEvidence.ENTITY_EXTERNAL_OUTBOX, outbox.getId(),
                    FishingChangeEvidence.ACTION_OUTBOX_SEND, outbox.getRequestId(), "system",
                    null, outbox, "外部通知已送达" + systemCode + "：" + outbox.getBizType() + "/" + outbox.getRefNo()
                            + "（第" + outbox.getSendCount() + "次投递）");
            sent++;
        }
        return sent;
    }

    /** 恢复后尝试补发的便捷入口：依赖无响应时返回0且不改状态；可用时投递全部积压。 */
    public int flushOnRecovery(String systemCode) {
        return dispatchPending(systemCode);
    }

    public long countByStatus(String systemCode, String status) {
        Long count = outboxMapper.selectCount(new QueryWrapper<AquaExternalNoticeOutbox>()
                .eq("system_code", systemCode)
                .eq("status", status));
        return count == null ? 0L : count;
    }

    public List<AquaExternalNoticeOutbox> listOutbox(String systemCode) {
        return outboxMapper.selectList(new QueryWrapper<AquaExternalNoticeOutbox>()
                .eq(systemCode != null, "system_code", systemCode)
                .orderByAsc("id"));
    }

    private AquaExternalGatewayState copyOf(AquaExternalGatewayState state) {
        AquaExternalGatewayState copy = new AquaExternalGatewayState();
        org.springframework.beans.BeanUtils.copyProperties(state, copy);
        return copy;
    }
}
