package com.beyond.specguard.auth.model.filter;

import com.beyond.specguard.admin.model.entity.InternalAdmin;
import com.beyond.specguard.admin.model.repository.InternalAdminRepository;
import com.beyond.specguard.admin.model.service.InternalAdminDetails;
import com.beyond.specguard.auth.exception.AuthException;
import com.beyond.specguard.auth.exception.errorcode.AuthErrorCode;
import com.beyond.specguard.company.common.model.entity.ClientUser;
import com.beyond.specguard.company.common.model.repository.ClientUserRepository;
import com.beyond.specguard.company.common.model.service.CustomUserDetails;
import com.beyond.specguard.company.common.model.service.RedisTokenService;
import com.beyond.specguard.auth.model.token.AdminAuthenticationToken;
import com.beyond.specguard.auth.model.token.ClientAuthenticationToken;
import com.beyond.specguard.common.jwt.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ClientUserRepository clientUserRepository;
    private final RedisTokenService redisTokenService;
    private final AuthenticationEntryPoint entryPoint;
    private final InternalAdminRepository internalAdminRepository;


    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/login")
                || path.startsWith("/api/v1/auth/signup")
                || path.startsWith("/api/v1/auth/token/refresh")
                || path.startsWith("/api/v1/auth/token")
                || path.startsWith("/api/v1/auth/invite");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authorization = request.getHeader("Authorization");
        log.debug(">>> JwtFilter 진입: path={}, Authorization={}", request.getRequestURI(), authorization);

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring(7);

        try {
            //  만료 여부 검증
            log.debug(">>> 토큰 만료 여부 검증 시작");
            jwtUtil.validateToken(token);
            log.debug(">>> 토큰 만료 여부 검증 통과");

            // 블랙리스트 확인
            String jti = jwtUtil.getJti(token);
            if (redisTokenService.isBlacklisted(jti)) {
                log.warn(">>> 블랙리스트 토큰 검출: jti={}", jti);
                throw new AuthException(AuthErrorCode.BLACKLISTED_ACCESS_TOKEN);
            }

            // 카테고리 확인
            String category = jwtUtil.getCategory(token);
            if (!"access".equals(category)) {
                log.warn(">>> 잘못된 토큰 카테고리 검출: category={}", category);
                throw new AuthException(AuthErrorCode.INVALID_TOKEN_CATEGORY);
            }

            // 사용자 조회
            String email = jwtUtil.getUsername(token);

            Authentication auth;

            log.debug("email : {}", email);

            // Admin 유저인지 Repository 조회로 확인
            Optional<InternalAdmin> adminOpt = internalAdminRepository.findByEmail(email);

            if (adminOpt.isPresent()) {
                InternalAdmin admin = adminOpt.get();

                InternalAdminDetails adminDetails = new InternalAdminDetails(admin);

                auth = new AdminAuthenticationToken(
                        adminDetails, null, adminDetails.getAuthorities());
            } else {
                ClientUser user = clientUserRepository.findByEmailWithCompany(email)
                        .orElseThrow(() -> {
                            log.warn(">>> 사용자 조회 실패: email={}", email);
                            return new AuthException(AuthErrorCode.USER_NOT_FOUND);
                        });


                CustomUserDetails userDetails = new CustomUserDetails(user);
                auth = new ClientAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()
                );
            }

            // 세션 검증
            String session = redisTokenService.getUserSession(email);
            if (session == null || !session.equals(jti)) {
                log.warn(">>> 세션 불일치: email={}, session={}, jti={}", email, session, jti);
                throw new AuthException(AuthErrorCode.SESSION_CONFLICT);
            }

            SecurityContextHolder.getContext().setAuthentication(auth);

            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            SecurityContextHolder.clearContext();
            log.error(">>> ExpiredJwtException 잡힘: token 만료", e);
            entryPoint.commence(request, response,
                    new AuthException(AuthErrorCode.ACCESS_TOKEN_EXPIRED));
        } catch (AuthException e) {
            SecurityContextHolder.clearContext();
            log.error(">>> AuthException 잡힘: code={}, message={}",
                    e.getErrorCode().getCode(), e.getMessage(), e);
            entryPoint.commence(request, response, e);
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            log.error(">>> Exception 잡힘: {}", e.getClass().getName(), e);
            entryPoint.commence(request, response,
                    new AuthException(AuthErrorCode.UNAUTHORIZED));
        }
    }
}
