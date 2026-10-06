package com.evops.aqua.fishing.controller;

import com.evops.aqua.fishing.dto.PointChangeApplyRequest;
import com.evops.aqua.fishing.dto.PointChangeReviewRequest;
import com.evops.aqua.fishing.entity.FishingPointChange;
import com.evops.aqua.fishing.service.FishingPointChangeService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收货点替换审批入口：申请（PENDING）、审批（通过才生效并通知客户 / 驳回不生效）。
 */
@RestController
@RequestMapping("/api/aqua/fishing/point-changes")
public class FishingPointChangeController {

    private final FishingPointChangeService changeService;

    public FishingPointChangeController(FishingPointChangeService changeService) {
        this.changeService = changeService;
    }

    @PostMapping
    public ApiResponse<FishingPointChange> apply(@RequestParam Long reservationId,
                                                 @Validated @RequestBody PointChangeApplyRequest request) {
        return ApiResponse.ok(changeService.apply(reservationId, request));
    }

    @PostMapping("/{id}/review")
    public ApiResponse<FishingPointChange> review(@PathVariable Long id,
                                                  @Validated @RequestBody PointChangeReviewRequest request) {
        return ApiResponse.ok(changeService.review(id, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<FishingPointChange> view(@PathVariable Long id) {
        return ApiResponse.ok(changeService.getOrThrow(id));
    }

    @GetMapping
    public ApiResponse<List<FishingPointChange>> list(@RequestParam(required = false) Long reservationId,
                                                      @RequestParam(required = false) String status) {
        return ApiResponse.ok(changeService.list(reservationId, status));
    }
}
