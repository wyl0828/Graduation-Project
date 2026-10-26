package cn.communityelderly.modules.elder.controller;

import cn.communityelderly.common.api.Result;
import cn.communityelderly.common.security.RequireRoles;
import cn.communityelderly.modules.elder.dto.CorrectionApplyDTO;
import cn.communityelderly.modules.elder.dto.CorrectionDetailVO;
import cn.communityelderly.modules.elder.dto.CorrectionReviewDTO;
import cn.communityelderly.modules.elder.service.ArchiveCorrectionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/elder/corrections")
public class ArchiveCorrectionController {

    private final ArchiveCorrectionService correctionService;

    public ArchiveCorrectionController(ArchiveCorrectionService correctionService) {
        this.correctionService = correctionService;
    }

    @PostMapping
    @RequireRoles({"CHILD"})
    public Result<Long> submitCorrection(@Valid @RequestBody CorrectionApplyDTO dto) {
        Long reqId = correctionService.submitCorrection(dto);
        return Result.success(reqId);
    }

    @GetMapping
    @RequireRoles({"COMMUNITY_ADMIN", "GRID_WORKER"})
    public Result<List<CorrectionDetailVO>> listCorrections(@RequestParam(required = false) String status) {
        return Result.success(correctionService.listCorrections(status));
    }

    @GetMapping("/{requestId}")
    @RequireRoles({"COMMUNITY_ADMIN", "GRID_WORKER"})
    public Result<CorrectionDetailVO> getCorrectionDetail(@PathVariable Long requestId) {
        return Result.success(correctionService.getCorrectionDetail(requestId));
    }

    @PostMapping("/{requestId}/review")
    @RequireRoles({"COMMUNITY_ADMIN"})
    public Result<Void> reviewCorrection(@PathVariable Long requestId, @Valid @RequestBody CorrectionReviewDTO dto) {
        correctionService.reviewCorrection(requestId, dto);
        return Result.success(null);
    }
}
