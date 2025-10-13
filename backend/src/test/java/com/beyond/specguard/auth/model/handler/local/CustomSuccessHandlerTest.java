package com.beyond.specguard.auth.model.handler.local;

import com.beyond.specguard.admin.model.entity.InternalAdmin;
import com.beyond.specguard.admin.model.service.InternalAdminDetails;
import com.beyond.specguard.company.common.model.entity.ClientCompany;
import com.beyond.specguard.company.common.model.entity.ClientUser;
import com.beyond.specguard.company.common.model.service.CustomUserDetails;
import com.beyond.specguard.company.common.model.service.RedisTokenService;
import com.beyond.specguard.common.jwt.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomSuccessHandlerTest {

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private RedisTokenService redisTokenService;
    @Mock
    private HttpServletResponse response;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private CustomSuccessHandler successHandler;

    private ClientUser mockUser;
    private ClientCompany mockCompany;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        mockCompany = ClientCompany.builder()
                .slug("testcorp")
                .name("테스트회사")
                .build();

        mockUser = ClientUser.builder()
                .email("user@test.com")
                .role(ClientUser.Role.OWNER)
                .company(mockCompany)
                .build();

        userDetails = new CustomUserDetails(mockUser);
    }

    @Test
    @DisplayName("✅ 기업 사용자 로그인 성공 시 AccessToken, RefreshToken, 쿠키, Redis 저장 검증")
    void onAuthenticationSuccess_companyUser() throws Exception {
        // given
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(jwtUtil.createAccessToken("user@test.com", "OWNER", "testcorp")).thenReturn("access-token");
        when(jwtUtil.createRefreshToken("user@test.com")).thenReturn("refresh-token");

        when(jwtUtil.getJti("access-token")).thenReturn("jti-uuid");
        when(jwtUtil.getExpiration("access-token")).thenReturn(new Date(System.currentTimeMillis() + 60000)); // 1분
        when(jwtUtil.getExpiration("refresh-token")).thenReturn(new Date(System.currentTimeMillis() + 120000)); // 2분

        // when
        successHandler.onAuthenticationSuccess(null, response, authentication);

        // then
        // JWT 생성 검증
        verify(jwtUtil).createAccessToken("user@test.com", "OWNER", "testcorp");
        verify(jwtUtil).createRefreshToken("user@test.com");

        // Redis 동작 검증
        verify(redisTokenService).deleteUserSession("user@test.com");
        verify(redisTokenService).deleteRefreshToken("user@test.com");
        verify(redisTokenService).saveRefreshToken(eq("user@test.com"), eq("refresh-token"), anyLong());
        verify(redisTokenService).saveUserSession(eq("user@test.com"), eq("jti-uuid"), anyLong());

        // 응답 헤더/쿠키 검증
        verify(response).setHeader("Authorization", "Bearer access-token");
        verify(response).setStatus(200);
        verify(response).addCookie(argThat(cookie ->
                "refresh_token".equals(cookie.getName())
                        && "refresh-token".equals(cookie.getValue())
                        && cookie.isHttpOnly()
        ));
    }

    @Test
    @DisplayName("✅ 관리자 로그인 성공 시 AccessToken, RefreshToken 정상 발급 및 Redis 저장")
    void onAuthenticationSuccess_adminUser() throws Exception {
        // given
        InternalAdmin admin = InternalAdmin.builder()
                .email("admin@test.com")
                .role(InternalAdmin.Role.ADMIN)
                .build();
        InternalAdminDetails adminDetails = new InternalAdminDetails(admin);

        when(authentication.getPrincipal()).thenReturn(adminDetails);
        when(jwtUtil.createAccessToken("admin@test.com", "MASTER", null)).thenReturn("access-token-admin");
        when(jwtUtil.createRefreshToken("admin@test.com")).thenReturn("refresh-token-admin");
        when(jwtUtil.getJti("access-token-admin")).thenReturn("jti-admin");
        when(jwtUtil.getExpiration(anyString())).thenReturn(new Date(System.currentTimeMillis() + 60000));

        // when
        successHandler.onAuthenticationSuccess(null, response, authentication);

        // then
        verify(jwtUtil).createAccessToken("admin@test.com", "MASTER", null);
        verify(jwtUtil).createRefreshToken("admin@test.com");
        verify(redisTokenService).deleteUserSession("admin@test.com");
        verify(redisTokenService).deleteRefreshToken("admin@test.com");
        verify(redisTokenService).saveRefreshToken(eq("admin@test.com"), eq("refresh-token-admin"), anyLong());
        verify(redisTokenService).saveUserSession(eq("admin@test.com"), eq("jti-admin"), anyLong());
        verify(response).setHeader("Authorization", "Bearer access-token-admin");
        verify(response).addCookie(any(Cookie.class));
    }
}
