package cn.communityelderly.modules.medication.service;

import cn.communityelderly.modules.medication.dto.MedicationCheckinDTO;
import cn.communityelderly.modules.medication.dto.MedicationTaskVO;

import java.util.List;

public interface MedicationTaskService {

    List<MedicationTaskVO> getTodayTasks(Long elderId);

    MedicationTaskVO checkin(MedicationCheckinDTO dto);
}
