package cn.communityelderly.modules.elder.service;

import cn.communityelderly.modules.auth.dto.AuthTokenResponse;
import cn.communityelderly.modules.elder.dto.ActivationCodeIssueDTO;
import cn.communityelderly.modules.elder.dto.ActivationConsumeDTO;
import cn.communityelderly.modules.elder.dto.ActivationTicketVO;

public interface ActivationCodeService {

    ActivationTicketVO issueActivationTicket(Long elderId, ActivationCodeIssueDTO dto);

    AuthTokenResponse consumeActivationCode(ActivationConsumeDTO dto);

    ActivationTicketVO getActiveTicket(Long elderId);
}
