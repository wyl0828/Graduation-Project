package cn.communityelderly.modules.order.service;

import cn.communityelderly.modules.order.dto.WorkOrderDispatchDTO;
import cn.communityelderly.modules.order.dto.WorkOrderExecutionDTO;
import cn.communityelderly.modules.order.dto.WorkOrderReviewDTO;
import cn.communityelderly.modules.order.dto.WorkOrderVO;
import cn.communityelderly.modules.order.dto.ServiceStopRequestDTO;
import cn.communityelderly.modules.order.dto.ServiceStopRequestVO;
import cn.communityelderly.modules.order.dto.ServiceStopReviewDTO;
import cn.communityelderly.modules.order.dto.WorkOrderHandoffRequestDTO;
import cn.communityelderly.modules.order.dto.WorkOrderHandoffAcceptDTO;
import cn.communityelderly.modules.order.dto.WorkOrderHandoffRejectDTO;

import java.util.List;

public interface WorkOrderService {
    List<WorkOrderVO> getDispatchPool(Long communityId, String status);
    List<WorkOrderVO> getMyTasks(Long workerUserId, String status);
    WorkOrderVO getOrderDetail(Long workOrderId);
    void dispatchOrder(Long workOrderId, WorkOrderDispatchDTO dto, Long operatorId);
    void acceptOrder(Long workOrderId, Long workerUserId);
    void rejectOrder(Long workOrderId, String reason, Long workerUserId);
    void startService(Long workOrderId, Long workerUserId);
    void submitExecutionResult(Long workOrderId, WorkOrderExecutionDTO dto, Long workerUserId);
    void assertCurrentCareWorkerMayUpload(Long workOrderId);
    void reviewWorkOrder(Long workOrderId, WorkOrderReviewDTO dto, Long reviewerId);
    Long requestServiceStop(Long workOrderId, ServiceStopRequestDTO dto, Long applicantUserId);
    List<ServiceStopRequestVO> listServiceStopRequests(Long workOrderId);
    void reviewServiceStop(Long workOrderId, Long stopId, ServiceStopReviewDTO dto, Long reviewerId);
    Long requestHandoff(Long workOrderId, WorkOrderHandoffRequestDTO dto, Long operatorId);
    void acceptHandoff(Long workOrderId, Long handoffId, WorkOrderHandoffAcceptDTO dto, Long workerUserId);
    void rejectHandoff(Long workOrderId, Long handoffId, WorkOrderHandoffRejectDTO dto, Long workerUserId);

    void terminateBeforeAcceptance(Long workOrderId, Long operatorId, String reason);
    void markOpenOrdersForWorkerHandoff(Long workerUserId, Long communityId, Long operatorId, String reason);
    void assertElderMayBeDeactivated(Long elderId);
}
