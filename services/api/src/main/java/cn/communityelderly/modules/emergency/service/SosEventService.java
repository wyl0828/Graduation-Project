package cn.communityelderly.modules.emergency.service;

import cn.communityelderly.modules.emergency.dto.*;

import java.util.List;

public interface SosEventService {

    SosEventVO triggerSos(SosTriggerDTO dto);

    void cancelSosByElder(Long sosId, SosCancelDTO dto);

    void dispatchSos(Long sosId, SosDispatchDTO dto);

    void closeSos(Long sosId, SosCloseDTO dto);

    SosEventVO getActiveSos(Long elderId);

    List<SosEventVO> listSosEvents(String status);
}
