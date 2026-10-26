package cn.communityelderly.modules.elder.controller;

import cn.communityelderly.common.api.Result;
import cn.communityelderly.modules.auth.dto.AuthTokenResponse;
import cn.communityelderly.modules.elder.dto.ActivationConsumeDTO;
import cn.communityelderly.modules.elder.service.ActivationCodeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
public class ElderActivationController {

    private final ActivationCodeService activationCodeService;

    public ElderActivationController(ActivationCodeService activationCodeService) {
        this.activationCodeService = activationCodeService;
    }

    @PostMapping("/api/v1/elder/activation/consume")
    public Result<AuthTokenResponse> consumeActivationCode(@Valid @RequestBody ActivationConsumeDTO dto) {
        return Result.success(activationCodeService.consumeActivationCode(dto));
    }

    @PostMapping("/api/v1/auth/activate-elder")
    public Result<AuthTokenResponse> activateElder(@Valid @RequestBody ActivationConsumeDTO dto) {
        return Result.success(activationCodeService.consumeActivationCode(dto));
    }
}
