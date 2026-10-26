package cn.communityelderly.modules.health.controller;

import cn.communityelderly.common.api.Result;
import cn.communityelderly.common.idempotency.Idempotency;
import cn.communityelderly.common.security.RequireRoles;
import cn.communityelderly.modules.health.dto.VitalRecordCreateDTO;
import cn.communityelderly.modules.health.dto.VitalTrendQueryDTO;
import cn.communityelderly.modules.health.dto.VitalTrendVO;
import cn.communityelderly.modules.health.service.VitalRecordService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/health/vitals")
public class VitalRecordController {

    private final VitalRecordService vitalRecordService;

    public VitalRecordController(VitalRecordService vitalRecordService) {
        this.vitalRecordService = vitalRecordService;
    }

    @PostMapping
    @Idempotency(required = false)
    @RequireRoles({"COMMUNITY_ADMIN"})
    public Result<Long> recordVital(@Valid @RequestBody VitalRecordCreateDTO dto) {
        Long vitalId = vitalRecordService.recordVital(dto);
        return Result.success(vitalId);
    }

    @GetMapping("/trend")
    @RequireRoles({"ELDER", "CHILD", "COMMUNITY_ADMIN"})
    public Result<VitalTrendVO> queryTrend(@Valid VitalTrendQueryDTO query) {
        VitalTrendVO vo = vitalRecordService.queryTrend(query);
        return Result.success(vo);
    }

    @GetMapping("/latest")
    @RequireRoles({"ELDER", "CHILD", "COMMUNITY_ADMIN"})
    public Result<VitalTrendVO.DataPoint> getLatestVital(@RequestParam Long elderId, @RequestParam(required = false) String vitalType) {
        VitalTrendVO.DataPoint dataPoint = vitalRecordService.getLatestVital(elderId, vitalType);
        return Result.success(dataPoint);
    }
}
