package com.evops.aqua.fishing.controller;

import com.evops.aqua.fishing.dto.BoxHandoverRequest;
import com.evops.aqua.fishing.dto.BoxStoreRequest;
import com.evops.aqua.fishing.entity.FishingColdBox;
import com.evops.aqua.fishing.service.FishingColdBoxService;
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
 * 冷藏箱入口：捕捞后入冷藏库（记录入库温度/箱号/称重）、交接复称（超差异规则冻结该箱）。
 */
@RestController
@RequestMapping("/api/aqua/fishing/cold-boxes")
public class FishingColdBoxController {

    private final FishingColdBoxService boxService;

    public FishingColdBoxController(FishingColdBoxService boxService) {
        this.boxService = boxService;
    }

    @PostMapping
    public ApiResponse<FishingColdBox> store(@RequestParam Long reservationId,
                                             @Validated @RequestBody BoxStoreRequest request) {
        return ApiResponse.ok(boxService.store(reservationId, request));
    }

    @GetMapping
    public ApiResponse<List<FishingColdBox>> listByOrder(@RequestParam(required = false) Long orderId,
                                                         @RequestParam(required = false) Long reservationId) {
        if (reservationId != null) {
            return ApiResponse.ok(boxService.boxesOfReservation(reservationId));
        }
        return ApiResponse.ok(boxService.boxesOfOrder(orderId));
    }

    @GetMapping("/{id}")
    public ApiResponse<FishingColdBox> view(@PathVariable Long id) {
        return ApiResponse.ok(boxService.getOrThrow(id));
    }

    @PostMapping("/{id}/handover")
    public ApiResponse<FishingColdBox> handover(@PathVariable Long id,
                                                @Validated @RequestBody BoxHandoverRequest request) {
        return ApiResponse.ok(boxService.handover(id, request));
    }
}
