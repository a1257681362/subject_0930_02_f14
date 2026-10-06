package com.evops.aqua.fishing;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.fishing.entity.AquaExternalNoticeOutbox;
import com.evops.aqua.fishing.entity.FishingBoxRule;
import com.evops.aqua.fishing.entity.FishingChangeEvidence;
import com.evops.aqua.fishing.entity.FishingColdBox;
import com.evops.aqua.fishing.entity.FishingDeliveryPoint;
import com.evops.aqua.fishing.entity.FishingNotification;
import com.evops.aqua.fishing.entity.FishingOrder;
import com.evops.aqua.fishing.entity.FishingOrderLine;
import com.evops.aqua.fishing.entity.FishingPointChange;
import com.evops.aqua.fishing.entity.FishingReservation;
import com.evops.aqua.fishing.mapper.AquaExternalNoticeOutboxMapper;
import com.evops.aqua.fishing.mapper.FishingBoxRuleMapper;
import com.evops.aqua.fishing.mapper.FishingChangeEvidenceMapper;
import com.evops.aqua.fishing.mapper.FishingColdBoxMapper;
import com.evops.aqua.fishing.mapper.FishingDeliveryPointMapper;
import com.evops.aqua.fishing.mapper.FishingNotificationMapper;
import com.evops.aqua.fishing.mapper.FishingOrderLineMapper;
import com.evops.aqua.fishing.mapper.FishingOrderMapper;
import com.evops.aqua.fishing.mapper.FishingReservationMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 渔获订单履约扩展验收（aqua-14）。
 * 主线：订单按规格和收货点拆分（单价/总金额创建即冻结）→ 二次拆单金额守恒
 * → 捕捞后冷藏箱逐箱登记入库温度/箱号/称重 → 交接复称净重，差异超规则冻结该箱、冻结箱不得交付
 * → 替换收货点须审批，通过才生效并通知客户 → 客户门户可见签收与差异但看不到内部成本/金额
 * → 外部海水养殖管理无响应时拆分明确失败不假装成功，恢复后凭原请求号成功且补发绝不重复通知。
 *
 * 独立内存库 profile（fishing），避免与其它共享库验收测试相互污染计数。
 */
@ActiveProfiles("fishing")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FishingFulfillmentAcceptanceTest {

    @Autowired private TestRestTemplate rest;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private FishingOrderMapper orderMapper;
    @Autowired private FishingOrderLineMapper orderLineMapper;
    @Autowired private FishingReservationMapper reservationMapper;
    @Autowired private FishingDeliveryPointMapper pointMapper;
    @Autowired private FishingBoxRuleMapper boxRuleMapper;
    @Autowired private FishingColdBoxMapper coldBoxMapper;
    @Autowired private FishingNotificationMapper notificationMapper;
    @Autowired private AquaExternalNoticeOutboxMapper outboxMapper;
    @Autowired private FishingChangeEvidenceMapper evidenceMapper;

    private final List<String> report = new ArrayList<>();
    private String sessionCookie;

    private Long point1, point2, point3;
    private Long binM;
    private Long order1;
    private Long resvL;   // L 200kg @10 收货点1
    private Long resvM;   // M 150kg @8 收货点2

    @Test
    void acceptanceFishingFulfillment() {
        checkpoint("CP0", "登录认证", () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("username", "bootstrap");
            body.put("password", "bootstrap");
            ResponseEntity<String> resp = call(HttpMethod.POST, "/api/auth/login", body);
            assertSuccess(json(resp), "登录");
            List<String> cookies = resp.getHeaders().get(HttpHeaders.SET_COOKIE);
            check(cookies != null && !cookies.isEmpty(), "登录成功但未下发会话Cookie");
            sessionCookie = cookies.get(0).split(";")[0];
        });

        checkpoint("CP1", "发布分级规则、登记三个收货点", () -> {
            JsonNode rule = json(call(HttpMethod.POST, "/api/aqua/fishing/grading-rules",
                    ruleBody("ff-rule-v1", line("S", "100", "300"), line("M", "300", "500"), line("L", "500", "800"))));
            assertSuccess(rule, "发布分级规则");
            point1 = registerPoint("DP-1", "门店甲", "fp-1");
            point2 = registerPoint("DP-2", "门店乙", "fp-2");
            point3 = registerPoint("DP-3", "门店丙", "fp-3");
            check(pointMapper.selectCount(new QueryWrapper<>()) == 3L, "应登记3个收货点");
        });

        checkpoint("CP2", "订单按规格+收货点拆分；单价/行金额/总金额创建即冻结", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/fishing/orders",
                    orderBody("海天酒楼", "ff-order-1",
                            orderLine("L", "500", "800", "2026-10-08T08:00:00", "200", point1, "10"),
                            orderLine("M", "300", "500", "2026-10-08T08:00:00", "150", point2, "8"))));
            assertSuccess(resp, "创建订单1");
            order1 = resp.path("data").path("id").asLong();
            FishingOrder order = orderMapper.selectById(order1);
            check(order.getTotalAmount().compareTo(new BigDecimal("3200.00")) == 0,
                    "订单总金额应为 200*10+150*8=3200，实际" + order.getTotalAmount());

            List<FishingReservation> reservations = reservationsOf(order1);
            check(reservations.size() == 2, "应按2行拆分为2条预约，实际" + reservations.size());
            resvL = reservations.get(0).getId();
            resvM = reservations.get(1).getId();
            FishingReservation rL = reservationMapper.selectById(resvL);
            check(rL.getPlannedAmount().compareTo(new BigDecimal("2000.00")) == 0,
                    "L预约应分劈金额2000，实际" + rL.getPlannedAmount());
            check(point1.equals(rL.getDeliveryPointId()) && "门店甲".equals(rL.getDeliveryPointName()),
                    "L预约应带收货点门店甲");
            List<FishingOrderLine> lines = orderLineMapper.selectList(
                    new QueryWrapper<FishingOrderLine>().eq("order_id", order1).orderByAsc("id"));
            check(lines.size() == 2 && lines.get(0).getLineAmount().compareTo(new BigDecimal("2000.00")) == 0
                            && lines.get(1).getLineAmount().compareTo(new BigDecimal("1200.00")) == 0,
                    "订单行金额应分别为2000/1200");
        });

        checkpoint("CP3", "二次拆单按规格+收货点拆分；金额守恒，订单总金额口径不变", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/fishing/reservations/" + resvL + "/split",
                    splitBody("ff-split-l",
                            splitPart("120", point1), splitPart("80", point3))));
            assertSuccess(resp, "L预约二次拆分为120/80两段");
            check(resp.path("data").size() == 2, "应返回母预约+1条子预约，实际" + resp.path("data").size());
            List<FishingReservation> family = childrenOf(resvL);
            check(family.size() == 1, "L预约应新增1条子预约，实际" + family.size());
            FishingReservation mother = reservationMapper.selectById(resvL);
            FishingReservation child = family.get(0);
            check(mother.getPlannedQtyKg().compareTo(new BigDecimal("120.00")) == 0,
                    "母预约承接首段120kg");
            check(mother.getPlannedAmount().compareTo(new BigDecimal("1200.00")) == 0
                    && child.getPlannedAmount().compareTo(new BigDecimal("800.00")) == 0,
                    "金额应按原单价分劈1200/800");
            check(resvL.equals(child.getSourceReservationId()) && child.getOrderLineId().equals(mother.getOrderLineId()),
                    "子预约应挂靠同一母预约与同一订单行");
            check(point3.equals(child.getDeliveryPointId()) && "门店丙".equals(child.getDeliveryPointName()),
                    "子预约应带收货点门店丙");

            // 守恒：同一订单行预约金额合计=行金额；订单总金额不变
            BigDecimal amountSum = mother.getPlannedAmount().add(child.getPlannedAmount());
            check(amountSum.compareTo(new BigDecimal("2000.00")) == 0,
                    "拆分前后行金额合计必须等于2000，实际" + amountSum);
            check(orderMapper.selectById(order1).getTotalAmount().compareTo(new BigDecimal("3200.00")) == 0,
                    "拆单后订单总金额仍应为3200（口径不变）");

            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/fishing/reservations/" + resvL + "/split",
                    splitBody("ff-split-l", splitPart("120", point1), splitPart("80", point3))));
            assertSuccess(again, "重复二次拆分幂等");
            check(childrenOf(resvL).size() == 1, "重复拆分不得再新增子预约");
            check(outboxMapper.selectCount(new QueryWrapper<>()) == 1L,
                    "重复拆分不得重复入发件箱");

            // 网关可用时补发：入箱的1条送达；再次补发0条（同一业务不重复通知）
            JsonNode dispatch = json(call(HttpMethod.POST,
                    "/api/aqua/fishing/external-gateway/dispatch", null));
            check(dispatch.path("data").asInt() == 1, "首次补发应送达1条，实际" + dispatch.path("data").asInt());
            JsonNode dispatchNoop = json(call(HttpMethod.POST,
                    "/api/aqua/fishing/external-gateway/dispatch", null));
            check(dispatchNoop.path("data").asInt() == 0, "已送达后补发必须0条（不重复通知）");
        });

        checkpoint("CP4", "外部依赖无响应时拆分失败不假装成功；恢复后重试成功且补发不重复", () -> {
            JsonNode down = json(call(HttpMethod.POST,
                    "/api/aqua/fishing/external-gateway/state",
                    gatewayBody(false, "海水养殖管理接口无响应/超时")));
            assertSuccess(down, "置外部依赖为不可用");

            JsonNode failed = json(call(HttpMethod.POST, "/api/aqua/fishing/reservations/" + resvM + "/split",
                    splitBody("ff-split-m", splitPart("100", point2), splitPart("50", point3))));
            check(!failed.path("success").asBoolean(true), "外部依赖无响应时拆分必须失败");
            check(failed.path("message").asText().contains("无响应"),
                    "失败原因应说明外部系统无响应，实际：" + failed.path("message").asText());
            check(childrenOf(resvM).isEmpty(), "失败时不得创建任何子预约（不假装成功）");
            check(outboxMapper.selectCount(new QueryWrapper<>()) == 1L,
                    "失败时不得写发件箱（仍只有CP3一条）");

            // 无响应期间尝试补发也应为0，且不产生任何发送/重复
            JsonNode dispatchWhileDown = json(call(HttpMethod.POST,
                    "/api/aqua/fishing/external-gateway/dispatch", null));
            check(dispatchWhileDown.path("data").asInt() == 0, "依赖无响应时补发必须0条");
            check(countEvidence(FishingChangeEvidence.ACTION_OUTBOX_SEND, null) == 1L,
                    "无响应时补发不得产生发送证据");

            JsonNode recover = json(call(HttpMethod.POST,
                    "/api/aqua/fishing/external-gateway/state",
                    gatewayBody(true, "故障恢复")));
            assertSuccess(recover, "外部依赖恢复");

            JsonNode retry = json(call(HttpMethod.POST, "/api/aqua/fishing/reservations/" + resvM + "/split",
                    splitBody("ff-split-m", splitPart("100", point2), splitPart("50", point3))));
            assertSuccess(retry, "恢复后凭原请求号拆分成功");
            check(childrenOf(resvM).size() == 1, "恢复后应只创建1条子预约");
            check(outboxMapper.selectCount(new QueryWrapper<>()) == 2L,
                    "恢复后应只新增1条发件箱（共2条），无重复");

            JsonNode dispatched = json(call(HttpMethod.POST,
                    "/api/aqua/fishing/external-gateway/dispatch", null));
            check(dispatched.path("data").asInt() == 1, "恢复后应只补发新增的1条积压，实际"
                    + dispatched.path("data").asInt());
            JsonNode dispatchAgain = json(call(HttpMethod.POST,
                    "/api/aqua/fishing/external-gateway/dispatch", null));
            check(dispatchAgain.path("data").asInt() == 0, "再次补发应为0条（已送达不重复通知）");
            long sent = outboxMapper.selectCount(new QueryWrapper<AquaExternalNoticeOutbox>()
                    .eq("status", AquaExternalNoticeOutbox.STATUS_SENT));
            check(sent == 2L, "两次拆分各送达1条，共应2条SENT，实际" + sent);
        });

        checkpoint("CP5", "发布冷藏箱容差规则；登记库位；锁定/分仓；逐箱冷藏入库三要素", () -> {
            JsonNode rule = json(call(HttpMethod.POST, "/api/aqua/fishing/box-rules",
                    boxRuleBody("2", "2", "品控主管", "ff-boxrule-v1")));
            assertSuccess(rule, "发布容差规则：绝对2kg且差异率2%");
            check("FBR-V1".equals(rule.path("data").path("ruleNo").asText()), "首个容差规则应为FBR-V1");
            check(boxRuleMapper.selectCount(new QueryWrapper<>()) == 1L, "应只有1个容差规则版本");

            binM = registerBin("BIN-M-F", "M", "500", "ff-bin-m");

            JsonNode lock = json(call(HttpMethod.POST, "/api/aqua/fishing/reservations/" + resvM + "/lock",
                    lockBody(binM, "调度员", "ff-lock-m")));
            assertSuccess(lock, "锁定M母预约");
            JsonNode load = json(call(HttpMethod.POST, "/api/aqua/fishing/reservations/" + resvM + "/loads",
                    loadBody("400", "100", "现场员", "ff-load-m")));
            assertSuccess(load, "M预约装100kg@400g");

            JsonNode boxA = json(call(HttpMethod.POST,
                    "/api/aqua/fishing/cold-boxes?reservationId=" + resvM,
                    boxStoreBody("CB-A", "-2.5", "50.000", "冷库员", "ff-box-a")));
            assertSuccess(boxA, "冷藏箱A入库");
            check("IN_STORE".equals(boxA.path("data").path("status").asText()), "入库后应为IN_STORE");
            check(new BigDecimal("-2.5").compareTo(boxA.path("data").path("inTempC").decimalValue()) == 0,
                    "应记录入库温度");
            check("CB-A".equals(boxA.path("data").path("boxNo").asText()), "应记录箱号");
            check(new BigDecimal("50.000").compareTo(boxA.path("data").path("inWeightKg").decimalValue()) == 0,
                    "应记录入库称重结果");

            JsonNode boxAAgain = json(call(HttpMethod.POST,
                    "/api/aqua/fishing/cold-boxes?reservationId=" + resvM,
                    boxStoreBody("CB-A", "-2.5", "50.000", "冷库员", "ff-box-a")));
            assertSuccess(boxAAgain, "重复入库幂等");
            check(boxAAgain.path("data").path("id").asLong() == boxA.path("data").path("id").asLong(),
                    "重复入库应返回同一箱");
            check(coldBoxMapper.selectCount(new QueryWrapper<>()) == 1L, "重复入库不得新增箱记录");

            JsonNode boxB = json(call(HttpMethod.POST,
                    "/api/aqua/fishing/cold-boxes?reservationId=" + resvM,
                    boxStoreBody("CB-B", "-2.3", "50.000", "冷库员", "ff-box-b")));
            assertSuccess(boxB, "冷藏箱B入库");
            long boxAId = boxA.path("data").path("id").asLong();
            long boxBId = boxB.path("data").path("id").asLong();

            JsonNode dupNo = json(call(HttpMethod.POST,
                    "/api/aqua/fishing/cold-boxes?reservationId=" + resvM,
                    boxStoreBody("CB-B", "-3.0", "60.000", "冷库员", "ff-box-b-dup")));
            check(!dupNo.path("success").asBoolean(true), "箱号重复应被拒绝");

            // A箱差异1kg/2%，恰在容差内 → 正常交接
            JsonNode handA = json(call(HttpMethod.POST, "/api/aqua/fishing/cold-boxes/" + boxAId + "/handover",
                    handoverBody("51.000", "交接员", "ff-hand-a")));
            assertSuccess(handA, "A箱交接复称51kg");
            check("HANDED_OVER".equals(handA.path("data").path("status").asText()),
                    "差异1kg且2%在容差内应正常交接");
            // B箱差异3kg，超过绝对2kg → 冻结
            JsonNode handB = json(call(HttpMethod.POST, "/api/aqua/fishing/cold-boxes/" + boxBId + "/handover",
                    handoverBody("53.000", "交接员", "ff-hand-b")));
            assertSuccess(handB, "B箱交接复称53kg");
            check("FROZEN".equals(handB.path("data").path("status").asText()),
                    "差异3kg超过2kg规则应冻结该箱，实际" + handB.path("data").path("status").asText());
            check(handB.path("data").path("frozenReason").asText().contains("冻结"),
                    "应记录冻结原因");

            JsonNode handBAgain = json(call(HttpMethod.POST, "/api/aqua/fishing/cold-boxes/" + boxBId + "/handover",
                    handoverBody("50.500", "交接员", "ff-hand-b-retry")));
            assertSuccess(handBAgain, "B箱重复交接幂等");
            check("FROZEN".equals(handBAgain.path("data").path("status").asText()),
                    "重复交接不得改变首次冻结结论");
        });

        checkpoint("CP6", "冻结箱不得交付：检验库位后完成预约仍被冻结箱拦截", () -> {
            JsonNode inspect = json(call(HttpMethod.POST, "/api/aqua/fishing/bins/" + binM + "/inspect",
                    actionBody("质检员", "ff-insp-m")));
            assertSuccess(inspect, "检验M库位");
            JsonNode done = json(call(HttpMethod.POST, "/api/aqua/fishing/reservations/" + resvM + "/complete",
                    actionBody("调度员", "ff-complete-m")));
            check(!done.path("success").asBoolean(true), "存在冻结箱时完成交付应被拒绝");
            check(done.path("message").asText().contains("冻结"), "拒绝原因应说明冷藏箱冻结不得交付");
            check(FishingReservation.STATUS_LOCKED.equals(reservationMapper.selectById(resvM).getStatus()),
                    "预约应仍为LOCKED，未交付");
        });

        checkpoint("CP7", "替换收货点须审批：申请不生效，通过才更新并通知客户；驳回不生效", () -> {
            // 母预约 resvL 现为120kg，收货点门店甲；申请改为门店丙
            JsonNode apply = json(call(HttpMethod.POST,
                    "/api/aqua/fishing/point-changes?reservationId=" + resvL,
                    pointApplyBody(point3, "客户临时改在门店丙交接", "调度员", "ff-pc-apply")));
            assertSuccess(apply, "提交收货点替换申请");
            Long changeId = apply.path("data").path("id").asLong();
            check("PENDING".equals(apply.path("data").path("status").asText()), "申请应为PENDING");
            check(point1.equals(reservationMapper.selectById(resvL).getDeliveryPointId()),
                    "审批前预约收货点必须维持门店甲不变");

            JsonNode duplicate = json(call(HttpMethod.POST,
                    "/api/aqua/fishing/point-changes?reservationId=" + resvL,
                    pointApplyBody(point3, "再次申请", "调度员", "ff-pc-apply-2")));
            check(!duplicate.path("success").asBoolean(true), "已有待审批申请时应拒绝再次申请");

            JsonNode approve = json(call(HttpMethod.POST,
                    "/api/aqua/fishing/point-changes/" + changeId + "/review",
                    reviewBody(true, "主管", "同意更换", "ff-pc-approve")));
            assertSuccess(approve, "审批通过");
            check("APPROVED".equals(approve.path("data").path("status").asText()), "应为APPROVED");
            FishingReservation updated = reservationMapper.selectById(resvL);
            check(point3.equals(updated.getDeliveryPointId()) && "门店丙".equals(updated.getDeliveryPointName()),
                    "审批通过后预约收货点才更新为门店丙");
            List<FishingNotification> notices = notificationMapper.selectList(
                    new QueryWrapper<FishingNotification>()
                            .eq("biz_type", FishingNotification.BIZ_POINT_CHANGE)
                            .eq("point_change_id", changeId));
            check(notices.size() == 1 && FishingNotification.STATUS_SENT.equals(notices.get(0).getStatus()),
                    "审批通过应只发一条客户通知");
            Long noticeId = notices.get(0).getId();

            JsonNode reApprove = json(call(HttpMethod.POST,
                    "/api/aqua/fishing/point-changes/" + changeId + "/review",
                    reviewBody(true, "主管", "重复审批", "ff-pc-approve-2")));
            assertSuccess(reApprove, "重复审批幂等");
            check(notificationMapper.selectCount(new QueryWrapper<FishingNotification>()
                    .eq("point_change_id", changeId)) == 1L, "重复审批不得重复通知");

            JsonNode ack = json(call(HttpMethod.POST, "/api/aqua/fishing/notifications/" + noticeId + "/ack",
                    ackBody("海天酒楼-王经理", "已知悉收货点改为门店丙", "ff-ack")));
            assertSuccess(ack, "客户签收收货点变更通知");

            // 子预约（门店丙）申请改为门店乙，驳回 → 收货点维持门店丙
            Long childResv = childrenOf(resvL).get(0).getId();
            JsonNode apply2 = json(call(HttpMethod.POST,
                    "/api/aqua/fishing/point-changes?reservationId=" + childResv,
                    pointApplyBody(point2, "想改门店乙", "调度员", "ff-pc-apply-r")));
            assertSuccess(apply2, "提交第二笔替换申请");
            Long changeId2 = apply2.path("data").path("id").asLong();
            JsonNode reject = json(call(HttpMethod.POST,
                    "/api/aqua/fishing/point-changes/" + changeId2 + "/review",
                    reviewBody(false, "主管", "门店乙线路已满，驳回", "ff-pc-reject")));
            assertSuccess(reject, "驳回第二笔申请");
            check("REJECTED".equals(reject.path("data").path("status").asText()), "应为REJECTED");
            check(point3.equals(reservationMapper.selectById(childResv).getDeliveryPointId()),
                    "驳回后预约收货点应维持门店丙不变");
        });

        checkpoint("CP8", "客户门户：可见签收与净重差异，但看不到任何内部成本/金额字段", () -> {
            ResponseEntity<String> raw = call(HttpMethod.GET,
                    "/api/aqua/fishing/customer/orders/" + order1, null);
            JsonNode portal = json(raw);
            assertSuccess(portal, "客户门户查询");
            String body = raw.getBody();
            check(body.contains("ackBy") && body.contains("已知悉收货点改为门店丙"),
                    "客户应能看到签收回执信息");
            check(body.contains("diffKg") && body.contains("FROZEN"),
                    "客户应能看到冷藏箱净重差异与冻结信息");
            check(body.contains("varianceType") || body.contains("coldBoxes"),
                    "客户门户应含差异/冷藏箱板块");
            for (String forbidden : new String[]{"totalAmount", "lineAmount", "unitPrice", "currency",
                    "inTempC", "costPrice", "internalCost"}) {
                check(!body.contains(forbidden), "客户门户不得泄露内部成本/金额字段：" + forbidden);
            }
        });

        checkpoint("CP9", "全程留证：拆分/入库/冻结/审批/网关/发件箱均有证据且关键动作不重复", () -> {
            check(countEvidence(FishingChangeEvidence.ACTION_RESV_SPLIT, null) >= 2L, "二次拆分应留证");
            check(countEvidence(FishingChangeEvidence.ACTION_BOX_STORE, null) == 2L,
                    "冷藏入库应留证2次（A/B）");
            check(countEvidence(FishingChangeEvidence.ACTION_BOX_FREEZE, null) == 1L,
                    "冻结应只留证1次（重复交接不重复冻结）");
            check(countEvidence(FishingChangeEvidence.ACTION_POINT_CHANGE_APPROVE, null) == 1L, "审批通过留证1次");
            check(countEvidence(FishingChangeEvidence.ACTION_POINT_CHANGE_REJECT, null) == 1L, "审批驳回留证1次");
            check(countEvidence(FishingChangeEvidence.ACTION_GATEWAY_DOWN, null) == 1L, "熔断置位留证");
            check(countEvidence(FishingChangeEvidence.ACTION_GATEWAY_UP, null) == 1L, "恢复置位留证");
            check(countEvidence(FishingChangeEvidence.ACTION_OUTBOX_SEND, null) == 2L,
                    "两次拆分各送达1条外部通知，共2条发送证据");
        });

        printReport();
    }

    // ------------------------------------------------------------------
    // 请求构造
    // ------------------------------------------------------------------

    private Long registerPoint(String no, String name, String requestId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("pointNo", no);
        body.put("pointName", name);
        body.put("address", name + "地址");
        body.put("operator", "仓管员");
        body.put("requestId", requestId);
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/fishing/delivery-points", body));
        assertSuccess(resp, "登记收货点" + no);
        return resp.path("data").path("id").asLong();
    }

    private Long registerBin(String binNo, String spec, String capacity, String requestId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("binNo", binNo);
        body.put("specCode", spec);
        body.put("capacityKg", new BigDecimal(capacity));
        body.put("operator", "仓管员");
        body.put("requestId", requestId);
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/fishing/bins", body));
        assertSuccess(resp, "登记库位" + binNo);
        return resp.path("data").path("id").asLong();
    }

    private Map<String, Object> ruleBody(String requestId, Map<String, Object>... lines) {
        Map<String, Object> body = new HashMap<>();
        body.put("lines", Arrays.asList(lines));
        body.put("publishedBy", "品控主管");
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> line(String spec, String minG, String maxG) {
        Map<String, Object> line = new HashMap<>();
        line.put("specCode", spec);
        line.put("minWeightG", new BigDecimal(minG));
        line.put("maxWeightG", new BigDecimal(maxG));
        return line;
    }

    private Map<String, Object> orderBody(String customer, String requestId, Map<String, Object>... lines) {
        Map<String, Object> body = new HashMap<>();
        body.put("customerName", customer);
        body.put("lines", Arrays.asList(lines));
        body.put("operator", "接单员");
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> orderLine(String spec, String minG, String maxG, String deliveryTime,
                                          String qtyKg, Long pointId, String unitPrice) {
        Map<String, Object> line = new HashMap<>();
        line.put("specCode", spec);
        line.put("weightMinG", new BigDecimal(minG));
        line.put("weightMaxG", new BigDecimal(maxG));
        line.put("deliveryTime", deliveryTime);
        line.put("plannedQtyKg", new BigDecimal(qtyKg));
        line.put("deliveryPointId", pointId);
        line.put("unitPrice", new BigDecimal(unitPrice));
        return line;
    }

    private Map<String, Object> splitBody(String requestId, Map<String, Object>... parts) {
        Map<String, Object> body = new HashMap<>();
        body.put("parts", Arrays.asList(parts));
        body.put("operator", "调度员");
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> splitPart(String qtyKg, Long pointId) {
        Map<String, Object> part = new HashMap<>();
        part.put("plannedQtyKg", new BigDecimal(qtyKg));
        part.put("deliveryPointId", pointId);
        return part;
    }

    private Map<String, Object> gatewayBody(boolean available, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("available", available);
        body.put("reason", reason);
        body.put("updatedBy", "运维");
        return body;
    }

    private Map<String, Object> boxRuleBody(String maxDiffKg, String maxDiffPct,
                                            String publishedBy, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("maxDiffKg", new BigDecimal(maxDiffKg));
        body.put("maxDiffPct", new BigDecimal(maxDiffPct));
        body.put("publishedBy", publishedBy);
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> lockBody(Long binId, String operator, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("binId", binId);
        body.put("operator", operator);
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> loadBody(String avgWeightG, String weightKg, String loadedBy, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("actualAvgWeightG", new BigDecimal(avgWeightG));
        body.put("weightKg", new BigDecimal(weightKg));
        body.put("loadedBy", loadedBy);
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> boxStoreBody(String boxNo, String tempC, String weightKg,
                                             String inBy, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("boxNo", boxNo);
        body.put("inTempC", new BigDecimal(tempC));
        body.put("inWeightKg", new BigDecimal(weightKg));
        body.put("inBy", inBy);
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> handoverBody(String weightKg, String handoverBy, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("handoverWeightKg", new BigDecimal(weightKg));
        body.put("handoverBy", handoverBy);
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> pointApplyBody(Long newPointId, String reason, String appliedBy, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("newPointId", newPointId);
        body.put("reason", reason);
        body.put("appliedBy", appliedBy);
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> reviewBody(boolean approved, String reviewedBy, String remark, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("approved", approved);
        body.put("reviewedBy", reviewedBy);
        body.put("reviewRemark", remark);
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> ackBody(String ackBy, String ackRemark, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("ackBy", ackBy);
        body.put("ackRemark", ackRemark);
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Object> actionBody(String operator, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("operator", operator);
        body.put("requestId", requestId);
        return body;
    }

    private List<FishingReservation> reservationsOf(Long orderId) {
        return reservationMapper.selectList(new QueryWrapper<FishingReservation>()
                .eq("order_id", orderId).orderByAsc("id"));
    }

    private List<FishingReservation> childrenOf(Long motherId) {
        return reservationMapper.selectList(new QueryWrapper<FishingReservation>()
                .eq("source_reservation_id", motherId).orderByAsc("id"));
    }

    private long countEvidence(String action, Long entityId) {
        QueryWrapper<FishingChangeEvidence> wrapper = new QueryWrapper<FishingChangeEvidence>()
                .eq("action", action);
        if (entityId != null) {
            wrapper.eq("entity_id", entityId);
        }
        Long count = evidenceMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    private void checkpoint(String id, String name, CheckpointBody body) {
        try {
            body.run();
            String line = "通过 " + id + " " + name;
            report.add(line);
            System.out.println("[验收] " + line);
        } catch (Throwable t) {
            String stop = "停点 " + id + "「" + name + "」失败，原因：" + t.getMessage();
            report.add(stop);
            report.add("验收结论：未通过，后续检查点未执行");
            printReport();
            fail("验收" + stop);
        }
    }

    private void printReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== 渔获订单履约扩展验收报告 ==========\n");
        for (String line : report) {
            sb.append(line).append('\n');
        }
        sb.append("=============================================\n");
        System.out.println(sb);
    }

    private ResponseEntity<String> call(HttpMethod method, String url, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (sessionCookie != null) {
            headers.add(HttpHeaders.COOKIE, sessionCookie);
        }
        return rest.exchange(url, method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private void assertSuccess(JsonNode node, String what) {
        if (!node.path("success").asBoolean(false)) {
            throw new IllegalStateException(what + "接口返回失败：" + node.path("message").asText());
        }
    }

    private void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private interface CheckpointBody {
        void run() throws Exception;
    }
}
