package com.evops.aqua.fishing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.fishing.entity.FishingColdBox;
import com.evops.aqua.fishing.entity.FishingNotification;
import com.evops.aqua.fishing.entity.FishingOrder;
import com.evops.aqua.fishing.entity.FishingReservation;
import com.evops.aqua.fishing.entity.FishingVariance;
import com.evops.aqua.fishing.mapper.FishingColdBoxMapper;
import com.evops.aqua.fishing.mapper.FishingNotificationMapper;
import com.evops.aqua.fishing.mapper.FishingVarianceMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户门户视图：客户可看订单进度、签收回执、冷藏箱净重差异与冻结情况、短装/超装数量差异；
 * 严格脱敏——不输出任何单价、行金额、订单总金额、币种等内部成本/金额字段，
 * 也不输出内部审计/主键操作者信息。
 */
@Service
public class FishingCustomerPortalService {

    private final FishingOrderService orderService;
    private final FishingColdBoxMapper coldBoxMapper;
    private final FishingNotificationMapper notificationMapper;
    private final FishingVarianceMapper varianceMapper;

    public FishingCustomerPortalService(FishingOrderService orderService,
                                        FishingColdBoxMapper coldBoxMapper,
                                        FishingNotificationMapper notificationMapper,
                                        FishingVarianceMapper varianceMapper) {
        this.orderService = orderService;
        this.coldBoxMapper = coldBoxMapper;
        this.notificationMapper = notificationMapper;
        this.varianceMapper = varianceMapper;
    }

    public Map<String, Object> orderView(Long orderId) {
        FishingOrder order = orderService.getOrThrow(orderId);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("orderNo", order.getOrderNo());
        view.put("status", order.getStatus());
        view.put("completedTime", order.getCompletedTime());
        // 注意：刻意不输出 totalAmount/currency/unitPrice 等任何金额成本字段

        List<Map<String, Object>> reservations = new ArrayList<>();
        for (FishingReservation r : orderService.reservationsOf(orderId)) {
            Map<String, Object> rv = new LinkedHashMap<>();
            rv.put("resvNo", r.getResvNo());
            rv.put("specCode", r.getSpecCode());
            rv.put("deliveryTime", r.getDeliveryTime());
            rv.put("deliveryPointName", r.getDeliveryPointName());
            rv.put("plannedQtyKg", r.getPlannedQtyKg());
            rv.put("actualQtyKg", r.getActualQtyKg());
            rv.put("vehicleNo", r.getVehicleNo());
            rv.put("status", r.getStatus());
            rv.put("variances", variancesOf(r.getId()));
            rv.put("coldBoxes", coldBoxesOf(r.getId()));
            reservations.add(rv);
        }
        view.put("reservations", reservations);
        view.put("notifications", notificationsOf(orderId));
        return view;
    }

    private List<Map<String, Object>> coldBoxesOf(Long reservationId) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<FishingColdBox> boxes = coldBoxMapper.selectList(new QueryWrapper<FishingColdBox>()
                .eq("reservation_id", reservationId).orderByAsc("id"));
        for (FishingColdBox box : boxes) {
            Map<String, Object> bv = new LinkedHashMap<>();
            bv.put("boxNo", box.getBoxNo());
            bv.put("specCode", box.getSpecCode());
            bv.put("status", box.getStatus());
            bv.put("inWeightKg", box.getInWeightKg());
            bv.put("handoverWeightKg", box.getHandoverWeightKg());
            bv.put("diffKg", box.getDiffKg());
            bv.put("diffPct", box.getDiffPct());
            // 差异与冻结信息对客户可见；入库温度属内部质控记录不对外
            bv.put("frozenReason", box.getFrozenReason());
            bv.put("handoverTime", box.getHandoverTime());
            result.add(bv);
        }
        return result;
    }

    private List<Map<String, Object>> variancesOf(Long reservationId) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<FishingVariance> variances = varianceMapper.selectList(new QueryWrapper<FishingVariance>()
                .eq("reservation_id", reservationId).orderByAsc("id"));
        for (FishingVariance v : variances) {
            Map<String, Object> vv = new LinkedHashMap<>();
            vv.put("varianceNo", v.getVarianceNo());
            vv.put("varianceType", v.getVarianceType());
            vv.put("plannedQtyKg", v.getPlannedQtyKg());
            vv.put("actualQtyKg", v.getActualQtyKg());
            vv.put("diffQtyKg", v.getDiffQtyKg());
            vv.put("status", v.getStatus());
            result.add(vv);
        }
        return result;
    }

    private List<Map<String, Object>> notificationsOf(Long orderId) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<FishingNotification> notifications = notificationMapper.selectList(
                new QueryWrapper<FishingNotification>().eq("order_id", orderId).orderByAsc("id"));
        for (FishingNotification n : notifications) {
            Map<String, Object> nv = new LinkedHashMap<>();
            nv.put("noticeNo", n.getNoticeNo());
            nv.put("bizType", n.getBizType());
            nv.put("message", n.getMessage());
            nv.put("status", n.getStatus());
            // 签收回执信息对客户可见
            nv.put("ackBy", n.getAckBy());
            nv.put("ackTime", n.getAckTime());
            nv.put("ackRemark", n.getAckRemark());
            result.add(nv);
        }
        return result;
    }
}
