package com.evops.aqua.fishing.controller;

import com.evops.aqua.fishing.dto.GatewayStateRequest;
import com.evops.aqua.fishing.entity.AquaExternalGatewayState;
import com.evops.aqua.fishing.entity.AquaExternalNoticeOutbox;
import com.evops.aqua.fishing.service.AquaExternalGatewayService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 外部依赖（海水养殖管理）入口：可用性置位（无响应熔断/恢复）、发件箱查询、恢复补发。
 */
@RestController
@RequestMapping("/api/aqua/fishing/external-gateway")
public class AquaExternalGatewayController {

    private final AquaExternalGatewayService gatewayService;

    public AquaExternalGatewayController(AquaExternalGatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @GetMapping("/state")
    public ApiResponse<AquaExternalGatewayState> state(
            @RequestParam(defaultValue = "MARICULTURE_MGMT") String systemCode) {
        return ApiResponse.ok(gatewayService.state(systemCode));
    }

    @PostMapping("/state")
    public ApiResponse<AquaExternalGatewayState> setState(
            @RequestParam(defaultValue = "MARICULTURE_MGMT") String systemCode,
            @Validated @RequestBody GatewayStateRequest request) {
        return ApiResponse.ok(gatewayService.setState(systemCode, request));
    }

    @GetMapping("/outbox")
    public ApiResponse<List<AquaExternalNoticeOutbox>> outbox(
            @RequestParam(required = false) String systemCode) {
        return ApiResponse.ok(gatewayService.listOutbox(systemCode));
    }

    /** 恢复后补发：只投递 PENDING/FAILED，SENT 永不重发；依赖仍无响应时返回0条。 */
    @PostMapping("/dispatch")
    public ApiResponse<Integer> dispatch(
            @RequestParam(defaultValue = "MARICULTURE_MGMT") String systemCode) {
        return ApiResponse.ok(gatewayService.flushOnRecovery(systemCode));
    }
}
