package cn.communityelderly.modules.medication.service;

import cn.communityelderly.modules.medication.dto.MedicationPlanCreateDTO;
import cn.communityelderly.modules.medication.dto.MedicationPlanUpdateDTO;
import cn.communityelderly.modules.medication.dto.MedicationPlanVO;
import cn.communityelderly.modules.medication.dto.MedicationPlanVersionVO;

import java.util.List;

public interface MedicationPlanService {

    MedicationPlanVO createPlan(MedicationPlanCreateDTO dto);

    MedicationPlanVO updatePlan(Long planId, MedicationPlanUpdateDTO dto);

    List<MedicationPlanVO> listPlans(Long elderId);

    List<MedicationPlanVersionVO> listPlanVersions(Long planId);

    MedicationPlanVO getPlanDetail(Long planId);
}
