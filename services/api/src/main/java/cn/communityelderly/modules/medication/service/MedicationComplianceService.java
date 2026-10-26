package cn.communityelderly.modules.medication.service;

import cn.communityelderly.modules.medication.dto.MedicationComplianceVO;
import cn.communityelderly.modules.medication.dto.MedicationTaskVO;

import java.util.List;

public interface MedicationComplianceService {

    MedicationComplianceVO getCompliance(Long elderId, Integer days);

    List<MedicationTaskVO> listCommunityTimeoutElders();

    void recordPhoneCare(Long taskId, String careNotes);
}
