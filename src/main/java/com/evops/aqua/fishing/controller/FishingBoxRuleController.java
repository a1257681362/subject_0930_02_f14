package com.evops.aqua.fishing.controller;

import com.evops.aqua.fishing.dto.BoxRulePublishRequest;
import com.evops.aqua.fishing.entity.FishingBoxRule;
import com.evops.aqua.fishing.service.FishingBoxRuleService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 冷藏箱交接净重容差规则入口：发布（新版本替代旧版本）、查询在效版本。
 */
@RestController
@RequestMapping("/api/aqua/fishing/box-rules")
public class FishingBoxRuleController {

    private final FishingBoxRuleService ruleService;

    public FishingBoxRuleController(FishingBoxRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @PostMapping
    public ApiResponse<FishingBoxRule> publish(@Validated @RequestBody BoxRulePublishRequest request) {
        return ApiResponse.ok(ruleService.publish(request));
    }

    @GetMapping("/active")
    public ApiResponse<FishingBoxRule> active() {
        return ApiResponse.ok(ruleService.activeRule());
    }
}
