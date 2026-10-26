package cn.communityelderly.modules.simulation.controller;

import cn.communityelderly.common.api.Result;
import cn.communityelderly.common.security.RequireRoles;
import cn.communityelderly.modules.simulation.dto.SimulationConfigDTO;
import cn.communityelderly.modules.simulation.dto.SimulationStatusVO;
import cn.communityelderly.modules.simulation.dto.SingleInjectDTO;
import cn.communityelderly.modules.simulation.service.SimulationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/simulation")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/start")
    @RequireRoles({"COMMUNITY_ADMIN"})
    public Result<Void> startSimulation(@Valid @RequestBody SimulationConfigDTO dto) {
        simulationService.startSimulation(dto);
        return Result.success(null);
    }

    @PostMapping("/stop")
    @RequireRoles({"COMMUNITY_ADMIN"})
    public Result<Void> stopSimulation(@RequestBody Map<String, Object> body) {
        Long elderId = Long.parseLong(body.getOrDefault("elderId", "1").toString());
        String vitalType = body.getOrDefault("vitalType", "BLOOD_PRESSURE").toString();
        simulationService.stopSimulation(elderId, vitalType);
        return Result.success(null);
    }

    @PostMapping("/inject-abnormal")
    @RequireRoles({"COMMUNITY_ADMIN", "GRID_WORKER"})
    public Result<Long> injectSingleAbnormal(@Valid @RequestBody SingleInjectDTO dto) {
        Long vitalId = simulationService.injectSingleAbnormal(dto);
        return Result.success(vitalId);
    }

    @GetMapping("/status")
    @RequireRoles({"COMMUNITY_ADMIN", "GRID_WORKER"})
    public Result<SimulationStatusVO> getStatus(@RequestParam(defaultValue = "1") Long elderId,
                                                @RequestParam(defaultValue = "BLOOD_PRESSURE") String vitalType) {
        return Result.success(simulationService.getStatus(elderId, vitalType));
    }
}
