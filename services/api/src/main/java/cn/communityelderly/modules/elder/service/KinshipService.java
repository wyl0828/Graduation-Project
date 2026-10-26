package cn.communityelderly.modules.elder.service;

import cn.communityelderly.modules.elder.dto.*;
import cn.communityelderly.modules.elder.entity.FamilyLinkApplication;

import java.util.List;

public interface KinshipService {

    Long applyKinship(KinshipApplyDTO dto);

    void reviewApplication(Long applicationId, KinshipReviewDTO dto);

    void freezeKinship(Long linkId, KinshipFreezeDTO dto);

    void unfreezeKinship(Long linkId, String reason);

    void unlinkKinship(Long linkId, String reason);

    List<BoundElderVO> listBoundElders(Long childUserId);

    List<FamilyLinkApplication> listApplications(String status);
}
