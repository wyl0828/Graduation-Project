package cn.communityelderly.modules.elder.service;

import cn.communityelderly.modules.elder.dto.CorrectionApplyDTO;
import cn.communityelderly.modules.elder.dto.CorrectionDetailVO;
import cn.communityelderly.modules.elder.dto.CorrectionReviewDTO;

import java.util.List;

public interface ArchiveCorrectionService {

    Long submitCorrection(CorrectionApplyDTO dto);

    void reviewCorrection(Long requestId, CorrectionReviewDTO dto);

    CorrectionDetailVO getCorrectionDetail(Long requestId);

    List<CorrectionDetailVO> listCorrections(String status);
}
