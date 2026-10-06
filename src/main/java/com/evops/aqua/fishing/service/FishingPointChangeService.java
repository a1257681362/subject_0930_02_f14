package com.evops.aqua.fishing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.fishing.dto.PointChangeApplyRequest;
import com.evops.aqua.fishing.dto.PointChangeReviewRequest;
import com.evops.aqua.fishing.entity.FishingChangeEvidence;
import com.evops.aqua.fishing.entity.FishingDeliveryPoint;
import com.evops.aqua.fishing.entity.FishingNotification;
import com.evops.aqua.fishing.entity.FishingPointChange;
import com.evops.aqua.fishing.entity.FishingReservation;
import com.evops.aqua.fishing.mapper.FishingNotificationMapper;
import com.evops.aqua.fishing.mapper.FishingPointChangeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 收货点替换审批：申请只落 PENDING，审批通过（APPROVED）才真正更新预约收货点并通知客户；
 * 驳回（REJECTED）不生效。申请/审批均 requestId 幂等，审批通过只通知一次。
 */
@Service
public class FishingPointChangeService {

    private final FishingPointChangeMapper changeMapper;
    private final FishingNotificationMapper notificationMapper;
    private final FishingReservationService reservationService;
    private final FishingDeliveryPointService pointService;
    private final FishingEvidenceService evidenceService;

    public FishingPointChangeService(FishingPointChangeMapper changeMapper,
                                     FishingNotificationMapper notificationMapper,
                                     FishingReservationService reservationService,
                                     FishingDeliveryPointService pointService,
                                     FishingEvidenceService evidenceService) {
        this.changeMapper = changeMapper;
        this.notificationMapper = notificationMapper;
        this.reservationService = reservationService;
        this.pointService = pointService;
        this.evidenceService = evidenceService;
    }

    /**
     * 提交替换申请：预约收货点此时不变。已完成预约不可申请；同一预约已有待审批申请时拒绝。
     */
    @Transactional(rollbackFor = Exception.class)
    public FishingPointChange apply(Long reservationId, PointChangeApplyRequest request) {
        FishingPointChange duplicated = changeMapper.selectOne(new QueryWrapper<FishingPointChange>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (duplicated != null) {
            return duplicated;
        }
        FishingReservation reservation = reservationService.getOrThrow(reservationId);
        if (com.evops.aqua.fishing.entity.FishingReservation.STATUS_COMPLETED.equals(reservation.getStatus())) {
            throw new IllegalArgumentException("已完成预约不可申请替换收货点");
        }
        FishingDeliveryPoint newPoint = pointService.requireActive(request.getNewPointId());
        if (newPoint.getId().equals(reservation.getDeliveryPointId())) {
            throw new IllegalArgumentException("新收货点与当前收货点相同，无需替换");
        }
        Long pendingCount = changeMapper.selectCount(new QueryWrapper<FishingPointChange>()
                .eq("reservation_id", reservationId)
                .eq("status", FishingPointChange.STATUS_PENDING));
        if (pendingCount != null && pendingCount > 0) {
            throw new IllegalArgumentException("预约" + reservation.getResvNo()
                    + "已有待审批的收货点替换申请，请先完成审批");
        }

        FishingPointChange change = new FishingPointChange();
        change.setChangeNo("FPC-" + reservation.getResvNo());
        change.setReservationId(reservationId);
        change.setOrderId(reservation.getOrderId());
        change.setOldPointId(reservation.getDeliveryPointId());
        change.setOldPointName(reservation.getDeliveryPointName());
        change.setNewPointId(newPoint.getId());
        change.setNewPointName(newPoint.getPointName());
        change.setReason(request.getReason());
        change.setStatus(FishingPointChange.STATUS_PENDING);
        change.setAppliedBy(request.getAppliedBy());
        change.setAppliedTime(LocalDateTime.now());
        change.setRequestId(request.getRequestId());
        changeMapper.insert(change);
        evidenceService.record(FishingChangeEvidence.ENTITY_POINT_CHANGE, change.getId(),
                FishingChangeEvidence.ACTION_POINT_CHANGE_APPLY, request.getRequestId(),
                request.getAppliedBy(), null, change, "预约" + reservation.getResvNo() + "申请收货点由"
                        + (change.getOldPointName() == null ? "（未指定）" : change.getOldPointName())
                        + "替换为" + newPoint.getPointName() + "，原因：" + request.getReason()
                        + "；待审批，当前收货点不变");
        return change;
    }

    /**
     * 审批：通过才更新预约收货点并通知客户；驳回不生效。已审批单据重复审批幂等返回首次结论。
     */
    @Transactional(rollbackFor = Exception.class)
    public FishingPointChange review(Long changeId, PointChangeReviewRequest request) {
        FishingPointChange change = getOrThrow(changeId);
        if (!FishingPointChange.STATUS_PENDING.equals(change.getStatus())) {
            return change;
        }
        FishingReservation reservation = reservationService.getOrThrow(change.getReservationId());
        FishingPointChange before = copyOf(change);
        change.setReviewedBy(request.getReviewedBy());
        change.setReviewTime(LocalDateTime.now());
        change.setReviewRemark(request.getReviewRemark());

        if (Boolean.TRUE.equals(request.getApproved())) {
            FishingDeliveryPoint newPoint = pointService.requireActive(change.getNewPointId());
            change.setStatus(FishingPointChange.STATUS_APPROVED);
            changeMapper.updateById(change);

            FishingReservation resvBefore = copyOfReservation(reservation);
            reservation.setDeliveryPointId(newPoint.getId());
            reservation.setDeliveryPointName(newPoint.getPointName());
            reservationService.update(reservation);

            FishingNotification notification = new FishingNotification();
            notification.setNoticeNo("FN-P" + change.getId());
            notification.setOrderId(change.getOrderId());
            notification.setReservationId(change.getReservationId());
            notification.setCustomerName(reservationService.customerNameOf(change.getOrderId()));
            notification.setChangeId(null);
            notification.setBizType(FishingNotification.BIZ_POINT_CHANGE);
            notification.setPointChangeId(change.getId());
            notification.setMessage("您的订单预约" + reservation.getResvNo() + "收货点已由"
                    + (change.getOldPointName() == null ? "（未指定）" : change.getOldPointName())
                    + "变更为" + newPoint.getPointName() + "，变更原因：" + change.getReason()
                    + "，已经审批通过，请按新收货点交接。");
            notification.setStatus(FishingNotification.STATUS_SENT);
            notification.setRequestId(request.getRequestId() + "#notice");
            notificationMapper.insert(notification);

            evidenceService.record(FishingChangeEvidence.ENTITY_POINT_CHANGE, change.getId(),
                    FishingChangeEvidence.ACTION_POINT_CHANGE_APPROVE, request.getRequestId(),
                    request.getReviewedBy(), before, change, "收货点替换审批通过：预约"
                            + reservation.getResvNo() + "收货点更新为" + newPoint.getPointName());
            evidenceService.record(FishingChangeEvidence.ENTITY_NOTIFICATION, notification.getId(),
                    FishingChangeEvidence.ACTION_NOTIFY_SEND, request.getRequestId(),
                    request.getReviewedBy(), null, notification,
                    "已向客户" + notification.getCustomerName() + "发送收货点变更通知"
                            + notification.getNoticeNo());
        } else {
            change.setStatus(FishingPointChange.STATUS_REJECTED);
            changeMapper.updateById(change);
            evidenceService.record(FishingChangeEvidence.ENTITY_POINT_CHANGE, change.getId(),
                    FishingChangeEvidence.ACTION_POINT_CHANGE_REJECT, request.getRequestId(),
                    request.getReviewedBy(), before, change, "收货点替换审批驳回：预约"
                            + reservation.getResvNo() + "收货点维持"
                            + (change.getOldPointName() == null ? "（未指定）" : change.getOldPointName())
                            + "不变" + (request.getReviewRemark() == null ? ""
                            : "，审批意见：" + request.getReviewRemark()));
        }
        return change;
    }

    public FishingPointChange getOrThrow(Long changeId) {
        FishingPointChange change = changeMapper.selectById(changeId);
        if (change == null) {
            throw new IllegalArgumentException("收货点替换申请不存在，id=" + changeId);
        }
        return change;
    }

    public java.util.List<FishingPointChange> list(Long reservationId, String status) {
        QueryWrapper<FishingPointChange> wrapper = new QueryWrapper<>();
        wrapper.eq(reservationId != null, "reservation_id", reservationId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return changeMapper.selectList(wrapper);
    }

    private FishingPointChange copyOf(FishingPointChange change) {
        FishingPointChange copy = new FishingPointChange();
        org.springframework.beans.BeanUtils.copyProperties(change, copy);
        return copy;
    }

    private FishingReservation copyOfReservation(FishingReservation reservation) {
        FishingReservation copy = new FishingReservation();
        org.springframework.beans.BeanUtils.copyProperties(reservation, copy);
        return copy;
    }
}
