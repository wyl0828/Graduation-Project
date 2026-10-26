package cn.communityelderly.modules.elder.controller;

import cn.communityelderly.common.api.Result;
import cn.communityelderly.common.security.ObjectAuthorizationPolicy;
import cn.communityelderly.common.security.RequireRoles;
import cn.communityelderly.modules.elder.dto.ElderArchiveVO;
import cn.communityelderly.modules.elder.dto.ElderProfileUpdateDTO;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.service.ElderArchiveService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/elder/profile")
public class ElderSelfProfileController {

    private final ElderArchiveService elderArchiveService;
    private final ObjectAuthorizationPolicy authorizationPolicy;

    public ElderSelfProfileController(ElderArchiveService elderArchiveService,
                                      ObjectAuthorizationPolicy authorizationPolicy) {
        this.elderArchiveService = elderArchiveService;
        this.authorizationPolicy = authorizationPolicy;
    }

    @GetMapping("/me")
    @RequireRoles({"ELDER"})
    public Result<ElderArchiveVO> getMyProfile() {
        Long elderId = authorizationPolicy.requireElderIdentityId();
        return Result.success(elderArchiveService.getElderById(elderId));
    }

    @PutMapping("/me")
    @RequireRoles({"ELDER"})
    public Result<ElderArchiveVO> updateMyProfile(@RequestBody ElderProfileUpdateDTO dto) {
        Long elderId = authorizationPolicy.requireElderIdentityId();
        ElderProfile updated = elderArchiveService.updateElderProfileSelf(elderId, dto);
        return Result.success(elderArchiveService.getElderById(updated.getElderId()));
    }
}
