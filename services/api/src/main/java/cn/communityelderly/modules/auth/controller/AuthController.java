package cn.communityelderly.modules.auth.controller;

import cn.communityelderly.common.api.Result;
import cn.communityelderly.common.security.AuthenticationFilter;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.auth.dto.*;
import cn.communityelderly.modules.auth.service.AuthService;
import cn.communityelderly.modules.auth.service.impl.PhoneVerificationChallengeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Cookie;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final PhoneVerificationChallengeService phoneVerificationChallengeService;
    private final boolean secureCookies;

    public AuthController(AuthService authService,
                          PhoneVerificationChallengeService phoneVerificationChallengeService,
                          @Value("${AUTH_COOKIE_SECURE:true}") boolean secureCookies) {
        this.authService = authService;
        this.phoneVerificationChallengeService = phoneVerificationChallengeService;
        this.secureCookies = secureCookies;
    }

    @PostMapping("/phone-challenges")
    public Result<PhoneChallengeResponse> issuePhoneChallenge(@Valid @RequestBody PhoneChallengeRequest request) {
        PhoneVerificationChallengeService.IssuedChallenge issued = phoneVerificationChallengeService.issue(
                request.getPhone(), request.getPurpose(), request.getRelatedRequestId(), null);
        return Result.success(new PhoneChallengeResponse(issued.challengeId(), issued.expiresAt()));
    }

    @PostMapping("/login")
    public Result<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request,
                                           HttpServletRequest httpRequest,
                                           jakarta.servlet.http.HttpServletResponse httpResponse) {
        AuthTokenResponse response = authService.login(request, httpRequest.getRemoteAddr());
        if (isWeb(response.getClientType())) {
            httpResponse.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            setSessionCookie(httpResponse, response.getToken(), response.getExpiresAt());
            response.setToken(null);
        }
        return Result.success(response);
    }

    @PostMapping("/register-child")
    public Result<AuthTokenResponse> registerChild(@Valid @RequestBody RegisterChildRequest request, HttpServletRequest httpRequest) {
        AuthTokenResponse response = authService.registerChild(request, httpRequest.getRemoteAddr());
        return Result.success(response);
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {
        String token = null;
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        }
        if (!StringUtils.hasText(token) && request.getCookies() != null) {
            token = java.util.Arrays.stream(request.getCookies())
                    .filter(cookie -> AuthenticationFilter.SESSION_COOKIE_NAME.equals(cookie.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }
        authService.logout(token);
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO).toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return Result.success(null);
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request,
                                       jakarta.servlet.http.HttpServletResponse response) {
        CurrentUser currentUser = UserContext.get();
        authService.changePassword(request);
        if (currentUser != null && isWeb(currentUser.getClientType())) {
            response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO).toString());
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        }
        return Result.success(null);
    }

    @GetMapping("/current-user")
    public Result<CurrentUser> getCurrentUser() {
        return Result.success(authService.getCurrentUser());
    }

    @PostMapping("/switch-role")
    public Result<AuthTokenResponse> switchRole(@Valid @RequestBody SwitchRoleRequest request,
                                                jakarta.servlet.http.HttpServletResponse httpResponse) {
        AuthTokenResponse response = authService.switchRole(request);
        if (isWeb(response.getClientType())) {
            httpResponse.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setToken(null);
        }
        return Result.success(response);
    }

    @PostMapping("/forgot-password/ticket")
    public Result<String> submitPasswordRecoveryTicket(@Valid @RequestBody PasswordRecoveryTicketDTO request) {
        String ticketNo = authService.submitPasswordRecoveryTicket(request);
        return Result.success(ticketNo);
    }

    private boolean isWeb(String clientType) {
        return "WEB".equalsIgnoreCase(clientType);
    }

    private void setSessionCookie(jakarta.servlet.http.HttpServletResponse response,
                                  String token,
                                  LocalDateTime expiresAt) {
        Duration maxAge = expiresAt == null
                ? Duration.ZERO
                : Duration.between(LocalDateTime.now(), expiresAt).isNegative()
                ? Duration.ZERO
                : Duration.between(LocalDateTime.now(), expiresAt);
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(token, maxAge).toString());
    }

    private ResponseCookie sessionCookie(String token, Duration maxAge) {
        return ResponseCookie.from(AuthenticationFilter.SESSION_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite("Lax")
                .path("/api/v1")
                .maxAge(maxAge)
                .build();
    }
}
