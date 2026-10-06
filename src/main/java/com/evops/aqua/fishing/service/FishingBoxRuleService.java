package com.evops.aqua.fishing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.fishing.dto.BoxRulePublishRequest;
import com.evops.aqua.fishing.entity.FishingBoxRule;
import com.evops.aqua.fishing.entity.FishingChangeEvidence;
import com.evops.aqua.fishing.mapper.FishingBoxRuleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 冷藏箱交接净重容差规则：差异绝对公斤数与百分比（按入库称重）任一超出即冻结该箱。
 * 新版本发布后旧版本停用；交接永远取当时 ACTIVE 版本。requestId 幂等。
 */
@Service
public class FishingBoxRuleService {

    private final FishingBoxRuleMapper ruleMapper;
    private final FishingEvidenceService evidenceService;

    public FishingBoxRuleService(FishingBoxRuleMapper ruleMapper,
                                 FishingEvidenceService evidenceService) {
        this.ruleMapper = ruleMapper;
        this.evidenceService = evidenceService;
    }

    @Transactional(rollbackFor = Exception.class)
    public FishingBoxRule publish(BoxRulePublishRequest request) {
        FishingBoxRule duplicated = ruleMapper.selectOne(new QueryWrapper<FishingBoxRule>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (duplicated != null) {
            return duplicated;
        }
        if (request.getMaxDiffKg() == null || request.getMaxDiffKg().signum() < 0) {
            throw new IllegalArgumentException("允许净重差异公斤数不能为负");
        }
        if (request.getMaxDiffPct() == null || request.getMaxDiffPct().signum() < 0) {
            throw new IllegalArgumentException("允许净重差异百分比不能为负");
        }
        List<FishingBoxRule> actives = activeRules();
        for (FishingBoxRule old : actives) {
            old.setStatus(FishingBoxRule.STATUS_DISABLED);
            ruleMapper.updateById(old);
            evidenceService.record(FishingChangeEvidence.ENTITY_BOX_RULE, old.getId(),
                    FishingChangeEvidence.ACTION_BOX_RULE_DISABLE, request.getRequestId(),
                    request.getPublishedBy(), null, old,
                    "冷藏箱容差规则" + old.getRuleNo() + "被新版本替代停用");
        }
        FishingBoxRule rule = new FishingBoxRule();
        rule.setRuleNo(nextRuleNo(actives.size()));
        rule.setVersionNo(actives.size() + 1);
        rule.setMaxDiffKg(request.getMaxDiffKg());
        rule.setMaxDiffPct(request.getMaxDiffPct());
        rule.setStatus(FishingBoxRule.STATUS_ACTIVE);
        rule.setPublishedBy(request.getPublishedBy());
        rule.setPublishedTime(LocalDateTime.now());
        rule.setRequestId(request.getRequestId());
        rule.setRemark(request.getRemark());
        ruleMapper.insert(rule);
        evidenceService.record(FishingChangeEvidence.ENTITY_BOX_RULE, rule.getId(),
                FishingChangeEvidence.ACTION_BOX_RULE_PUBLISH, request.getRequestId(),
                request.getPublishedBy(), null, rule,
                "发布冷藏箱容差规则" + rule.getRuleNo() + "：净重差异允许绝对" + rule.getMaxDiffKg()
                        + "kg、差异率" + rule.getMaxDiffPct() + "（按入库称重），任一超出冻结该箱");
        return rule;
    }

    /** 当前在效容差规则；没有任何规则时拒绝交接（无规则不能放行冻结判定）。 */
    public FishingBoxRule activeRule() {
        List<FishingBoxRule> actives = activeRules();
        if (actives.isEmpty()) {
            throw new IllegalArgumentException("尚未发布冷藏箱交接净重容差规则，无法判定差异是否冻结");
        }
        return actives.get(0);
    }

    /**
     * 判定净重差异是否超规则：绝对差公斤数或差异率（百分数值，如 5.0000 表示 5%）任一超出。
     * 返回 true 表示应冻结该箱。
     */
    public boolean exceeds(FishingBoxRule rule, BigDecimal inWeightKg, BigDecimal diffAbsKg) {
        if (diffAbsKg.compareTo(rule.getMaxDiffKg()) > 0) {
            return true;
        }
        if (inWeightKg.signum() > 0) {
            // 差异率（百分数）= 差异/入库重 *100
            BigDecimal pct = diffAbsKg.multiply(new BigDecimal("100"))
                    .divide(inWeightKg, 4, BigDecimal.ROUND_HALF_UP);
            return pct.compareTo(rule.getMaxDiffPct()) > 0;
        }
        return false;
    }

    private List<FishingBoxRule> activeRules() {
        return ruleMapper.selectList(new QueryWrapper<FishingBoxRule>()
                .eq("status", FishingBoxRule.STATUS_ACTIVE).orderByAsc("id"));
    }

    private String nextRuleNo(int activeCount) {
        Long total = ruleMapper.selectCount(new QueryWrapper<>());
        return "FBR-V" + (total + 1);
    }
}
