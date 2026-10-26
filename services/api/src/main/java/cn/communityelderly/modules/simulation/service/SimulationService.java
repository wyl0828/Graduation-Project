package cn.communityelderly.modules.simulation.service;

import cn.communityelderly.modules.simulation.dto.SimulationConfigDTO;
import cn.communityelderly.modules.simulation.dto.SimulationStatusVO;
import cn.communityelderly.modules.simulation.dto.SingleInjectDTO;

public interface SimulationService {

    void startSimulation(SimulationConfigDTO dto);

    void stopSimulation(Long elderId, String vitalType);

    Long injectSingleAbnormal(SingleInjectDTO dto);

    SimulationStatusVO getStatus(Long elderId, String vitalType);
}
