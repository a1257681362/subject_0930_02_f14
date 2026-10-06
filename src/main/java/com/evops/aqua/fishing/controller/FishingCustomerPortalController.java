package com.evops.aqua.fishing.controller;

import com.evops.aqua.fishing.service.FishingCustomerPortalService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 客户门户入口：客户可看订单进度、签收回执、净重差异与短装/超装信息；
 * 不返回任何单价/金额/成本字段（见 FishingCustomerPortalService 脱敏视图）。
 */
@RestController
@RequestMapping("/api/aqua/fishing/customer/orders")
public class FishingCustomerPortalController {

    private final FishingCustomerPortalService portalService;

    public FishingCustomerPortalController(FishingCustomerPortalService portalService) {
        this.portalService = portalService;
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> order(@PathVariable Long id) {
        return ApiResponse.ok(portalService.orderView(id));
    }
}
