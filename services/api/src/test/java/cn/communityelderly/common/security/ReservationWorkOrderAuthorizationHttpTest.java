package cn.communityelderly.common.security;

import cn.communityelderly.coordination.ReservationWorkOrderCoordinator;
import cn.communityelderly.modules.order.service.WorkOrderService;
import cn.communityelderly.modules.reservation.service.ServiceReservationService;
import cn.communityelderly.modules.auth.service.impl.AuthSessionAuthenticationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        cn.communityelderly.modules.reservation.controller.ServiceReservationController.class,
        cn.communityelderly.coordination.AdminReservationCoordinationController.class,
        cn.communityelderly.modules.order.controller.WorkOrderController.class,
        cn.communityelderly.modules.order.controller.WorkOrderExecutionController.class
})
class ReservationWorkOrderAuthorizationHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ServiceReservationService reservationService;

    @MockitoBean
    private ReservationWorkOrderCoordinator coordinator;

    @MockitoBean
    private WorkOrderService workOrderService;

    @MockitoBean
    private AuthSessionAuthenticationService sessionAuthenticationService;

    @Test
    @DisplayName("HTTP 层拒绝子女调用预约管理员确认/拒绝接口，协调服务不被触发")
    void childCannotReachReservationAdministrationRoutes() throws Exception {
        authenticate("child-session", child());

        mockMvc.perform(get("/api/v1/admin/reservations/pending")
                        .header("Authorization", "Bearer child-session"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/reservations/101/confirm")
                        .header("Authorization", "Bearer child-session"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/reservations/101/reject")
                        .header("Authorization", "Bearer child-session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"服务人员不足\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(coordinator);
    }

    @Test
    @DisplayName("HTTP 层拒绝老人派单或审核工单，服务调用不产生业务副作用")
    void elderCannotDispatchOrReviewWorkOrder() throws Exception {
        authenticate("elder-session", elder());

        mockMvc.perform(post("/api/v1/work-orders/201/dispatch")
                        .header("Authorization", "Bearer elder-session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeUserId\":88,\"reason\":\"本社区派单\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/work-orders/201/review")
                        .header("Authorization", "Bearer elder-session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\",\"reviewComment\":\"服务完成\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(workOrderService);
    }

    @Test
    @DisplayName("HTTP 层拒绝管理员代替原申请人取消预约")
    void administratorCannotCancelReservationOnApplicantsBehalf() throws Exception {
        authenticate("admin-session", admin());

        mockMvc.perform(post("/api/v1/service-reservations/101/cancel")
                        .header("Authorization", "Bearer admin-session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"改期\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(reservationService);
    }

    @Test
    @DisplayName("HTTP 层限制预约新增与取消为子女申请人，老人只能查看进度")
    void elderCannotCreateOrCancelReservation() throws Exception {
        authenticate("elder-booking-session", elder());

        mockMvc.perform(post("/api/v1/service-reservations")
                        .header("Authorization", "Bearer elder-booking-session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"elderId\":2,\"serviceType\":\"DAILY_CARE\",\"serviceDate\":\"2026-11-02\",\"slotCode\":\"MORNING\",\"serviceAddress\":\"测试地址\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/service-reservations/101/cancel")
                        .header("Authorization", "Bearer elder-booking-session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"改期\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(reservationService);
    }

    private void authenticate(String token, CurrentUser user) {
        when(sessionAuthenticationService.authenticate(eq(token), anyString())).thenReturn(user);
    }

    private static CurrentUser child() {
        return new CurrentUser("55", "13900000055", "FAMILY", List.of("CHILD"), "CHILD", "1", 501L, "s1", "MINIAPP");
    }

    private static CurrentUser elder() {
        return new CurrentUser("31", "13900000031", "ELDER", List.of("ELDER"), "ELDER", "1", 301L, "s2", "MINIAPP");
    }

    private static CurrentUser admin() {
        return new CurrentUser("10", "13900000010", "STAFF", List.of("COMMUNITY_ADMIN"), "COMMUNITY_ADMIN", "1", 101L, "s3", "WEB");
    }
}
