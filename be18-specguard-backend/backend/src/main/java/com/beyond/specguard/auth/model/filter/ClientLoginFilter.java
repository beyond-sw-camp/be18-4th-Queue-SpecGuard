package com.beyond.specguard.auth.model.filter;

import com.beyond.specguard.company.common.model.dto.request.LoginRequestDto;
import com.beyond.specguard.auth.model.handler.local.CustomFailureHandler;
import com.beyond.specguard.auth.model.handler.local.CustomSuccessHandler;
import com.beyond.specguard.auth.model.token.ClientAuthenticationToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

public class ClientLoginFilter extends UsernamePasswordAuthenticationFilter {

    public ClientLoginFilter(
            @Qualifier("clientAuthenticationManager") AuthenticationManager clientAuthenticationManager,
            CustomSuccessHandler customSuccessHandler,
            CustomFailureHandler customFailureHandler
    ) {
        super(clientAuthenticationManager);
        setAuthenticationSuccessHandler(customSuccessHandler);
        setAuthenticationFailureHandler(customFailureHandler);
        setFilterProcessesUrl("/api/v1/auth/login"); // 로그인 엔드포인트
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request,
                                                HttpServletResponse response) throws AuthenticationException {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            LoginRequestDto loginDTO = objectMapper.readValue(request.getInputStream(), LoginRequestDto.class);

            UsernamePasswordAuthenticationToken authToken =
                    new ClientAuthenticationToken(loginDTO.getEmail(), loginDTO.getPassword());

            return this.getAuthenticationManager().authenticate(authToken);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
