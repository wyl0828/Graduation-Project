package cn.communityelderly.modules.reservation;

import cn.communityelderly.coordination.AdminReservationCoordinationController;
import cn.communityelderly.modules.reservation.service.ServiceReservationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReservationCancellationApiContractTest {

    @Test
    @DisplayName("管理员接口提供取消申请列表与审批入口")
    void adminCanListPendingCancellationRequests() {
        RequestMapping baseMapping = AdminReservationCoordinationController.class
                .getAnnotation(RequestMapping.class);
        assertEquals("/api/v1/admin/reservations", baseMapping.value()[0]);

        Method[] methods = AdminReservationCoordinationController.class.getDeclaredMethods();
        boolean hasPendingCancellationQueue = Arrays.stream(methods)
                .map(method -> method.getAnnotation(GetMapping.class))
                .filter(mapping -> mapping != null)
                .flatMap(mapping -> Arrays.stream(mapping.value()))
                .anyMatch("/cancel-requests/pending"::equals);

        assertTrue(hasPendingCancellationQueue, "管理员需要查询待审批取消申请");
    }

    @Test
    @DisplayName("管理员接口提供取消申请审批入口")
    void adminCanReviewCancellationRequests() {
        boolean hasCancellationReviewEndpoint = Arrays.stream(AdminReservationCoordinationController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(mapping -> mapping != null)
                .flatMap(mapping -> Arrays.stream(mapping.value()))
                .anyMatch("/cancel-requests/{cancelId}/review"::equals);

        assertTrue(hasCancellationReviewEndpoint, "管理员需要审批取消申请");
    }

    @Test
    @DisplayName("预约服务公开待审取消申请查询操作")
    void reservationServiceListsPendingCancellationRequests() {
        boolean hasQueueOperation = Arrays.stream(ServiceReservationService.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("listPendingCancellationRequests"));

        assertTrue(hasQueueOperation, "预约服务需要提供待审申请列表");
    }

    @Test
    @DisplayName("预约服务公开取消申请审批操作")
    void reservationServiceReviewsCancellationRequests() {
        boolean hasReviewOperation = Arrays.stream(ServiceReservationService.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("reviewCancellationRequest"));

        assertTrue(hasReviewOperation, "预约服务需要提供取消申请审批操作");
    }
}
