package cn.communityelderly.modules.emergency.controller;

import cn.communityelderly.common.api.Result;
import cn.communityelderly.common.idempotency.Idempotency;
import cn.communityelderly.common.security.RequireRoles;
import cn.communityelderly.modules.emergency.dto.*;
import cn.communityelderly.modules.emergency.service.SosEventService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/emergency/sos")
public class SosEventController {

    private final SosEventService sosEventService;

    public SosEventController(SosEventService sosEventService) {
        this.sosEventService = sosEventService;
    }

    @PostMapping("/trigger")
    @Idempotency(required = false)
    @RequireRoles({"ELDER"})
    public Result<SosEventVO> triggerSos(@Valid @RequestBody SosTriggerDTO dto) {
        SosEventVO vo = sosEventService.triggerSos(dto);
        return Result.success(vo);
    }

    @PostMapping("/{sosId}/cancel")
    @RequireRoles({"ELDER"})
    public Result<Void> cancelSosByElder(@PathVariable Long sosId, @RequestBody(required = false) SosCancelDTO dto) {
        sosEventService.cancelSosByElder(sosId, dto);
        return Result.success(null);
    }

    @GetMapping("/active")
    @RequireRoles({"ELDER", "CHILD", "COMMUNITY_ADMIN", "GRID_WORKER"})
    public Result<SosEventVO> getActiveSos(@RequestParam(required = false) Long elderId) {
        return Result.success(sosEventService.getActiveSos(elderId));
    }

    @GetMapping("/list")
    @RequireRoles({"COMMUNITY_ADMIN", "GRID_WORKER"})
    public Result<List<SosEventVO>> listSosEvents(@RequestParam(required = false) String status) {
        return Result.success(sosEventService.listSosEvents(status));
    }

    @PostMapping("/{sosId}/dispatch")
    @RequireRoles({"COMMUNITY_ADMIN"})
    public Result<Void> dispatchSos(@PathVariable Long sosId, @Valid @RequestBody SosDispatchDTO dto) {
        sosEventService.dispatchSos(sosId, dto);
        return Result.success(null);
    }

    @PostMapping("/{sosId}/close")
    @RequireRoles({"COMMUNITY_ADMIN"})
    public Result<Void> closeSos(@PathVariable Long sosId, @Valid @RequestBody SosCloseDTO dto) {
        sosEventService.closeSos(sosId, dto);
        return Result.success(null);
    }
}
