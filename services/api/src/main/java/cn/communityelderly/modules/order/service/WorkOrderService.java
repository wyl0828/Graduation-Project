package cn.communityelderly.modules.order.service;

import cn.communityelderly.modules.order.dto.WorkOrderDispatchDTO;
import cn.communityelderly.modules.order.dto.WorkOrderExecutionDTO;
import cn.communityelderly.modules.order.dto.WorkOrderReviewDTO;
import cn.communityelderly.modules.order.dto.WorkOrderVO;

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
}
