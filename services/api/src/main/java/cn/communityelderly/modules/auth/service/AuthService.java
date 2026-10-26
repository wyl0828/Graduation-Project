package cn.communityelderly.modules.auth.service;

import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.modules.auth.dto.*;

public interface AuthService {

    AuthTokenResponse login(LoginRequest request);

    AuthTokenResponse login(LoginRequest request, String sourceIp);

    AuthTokenResponse registerChild(RegisterChildRequest request);

    AuthTokenResponse registerChild(RegisterChildRequest request, String sourceIp);

    void logout(String token);

    void changePassword(PasswordChangeRequest request);

    CurrentUser getCurrentUser();

    AuthTokenResponse switchRole(SwitchRoleRequest request);

    String submitPasswordRecoveryTicket(PasswordRecoveryTicketDTO request);
}
