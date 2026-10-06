package com.evops.aqua.fishing.controller;

import com.evops.aqua.fishing.dto.PointRegisterRequest;
import com.evops.aqua.fishing.entity.FishingDeliveryPoint;
import com.evops.aqua.fishing.service.FishingDeliveryPointService;
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
 * 收货点入口：登记、查询、停用。订单可按收货点拆分；停用点不得作为新收货点。
 */
@RestController
@RequestMapping("/api/aqua/fishing/delivery-points")
public class FishingDeliveryPointController {

    private final FishingDeliveryPointService pointService;

    public FishingDeliveryPointController(FishingDeliveryPointService pointService) {
        this.pointService = pointService;
    }

    @PostMapping
    public ApiResponse<FishingDeliveryPoint> register(@Validated @RequestBody PointRegisterRequest request) {
        return ApiResponse.ok(pointService.register(request));
    }

    @GetMapping
    public ApiResponse<List<FishingDeliveryPoint>> list(@RequestParam(required = false) String status) {
        return ApiResponse.ok(pointService.list(status));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<FishingDeliveryPoint> disable(@PathVariable Long id,
                                                      @RequestParam String operator) {
        return ApiResponse.ok(pointService.disable(id, operator));
    }
}
