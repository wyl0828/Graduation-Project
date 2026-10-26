package cn.communityelderly.modules.elder.controller;

import cn.communityelderly.common.api.PageResult;
import cn.communityelderly.common.api.Result;
import cn.communityelderly.common.security.RequireRoles;
import cn.communityelderly.modules.elder.dto.*;
import cn.communityelderly.modules.elder.service.ActivationCodeService;
import cn.communityelderly.modules.elder.service.ElderArchiveService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/elder/archives")
public class ElderArchiveController {

    private final ElderArchiveService elderArchiveService;
    private final ActivationCodeService activationCodeService;

    public ElderArchiveController(ElderArchiveService elderArchiveService,
                                  ActivationCodeService activationCodeService) {
        this.elderArchiveService = elderArchiveService;
        this.activationCodeService = activationCodeService;
    }

    @PostMapping
    @RequireRoles({"COMMUNITY_ADMIN"})
    public Result<ElderArchiveVO> createElder(@Valid @RequestBody ElderCreateDTO dto) {
        return Result.success(elderArchiveService.createElder(dto));
    }

    @GetMapping
    @RequireRoles({"COMMUNITY_ADMIN", "GRID_WORKER"})
    public Result<PageResult<ElderArchiveVO>> pageElders(ElderQueryDTO query) {
        return Result.success(elderArchiveService.pageElders(query));
    }

    @GetMapping("/{elderId}")
    @RequireRoles({"COMMUNITY_ADMIN", "GRID_WORKER", "CHILD", "ELDER"})
    public Result<ElderArchiveVO> getElderById(@PathVariable Long elderId) {
        return Result.success(elderArchiveService.getElderById(elderId));
    }

    @PostMapping("/{elderId}/status")
    @RequireRoles({"COMMUNITY_ADMIN"})
    public Result<Void> updateElderStatus(@PathVariable Long elderId, @RequestBody Map<String, String> body) {
        String status = body.getOrDefault("status", "INACTIVE");
        String reason = body.getOrDefault("reason", "管理员操作变更状态");
        elderArchiveService.updateElderStatus(elderId, status, reason);
        return Result.success(null);
    }

    @PostMapping("/{elderId}/activation-code")
    @RequireRoles({"COMMUNITY_ADMIN"})
    public Result<ActivationTicketVO> issueActivationCode(@PathVariable Long elderId, @RequestBody(required = false) ActivationCodeIssueDTO dto) {
        if (dto == null) {
            dto = new ActivationCodeIssueDTO();
        }
        ActivationTicketVO vo = activationCodeService.issueActivationTicket(elderId, dto);
        return Result.success(vo);
    }

    @GetMapping("/{elderId}/activation-code")
    @RequireRoles({"COMMUNITY_ADMIN"})
    public Result<ActivationTicketVO> getActiveActivationCode(@PathVariable Long elderId) {
        return Result.success(activationCodeService.getActiveTicket(elderId));
    }
}
