package com.beyond.specguard.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;

@OpenAPIDefinition(
        info = @Info(
                title = "SpecGuard API", 
                description = "SpecGuard API 테스트 문서",
                version = "v1.0.0"
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "로컬 개발 환경"),
                @Server(url = "http://specguard-api.beyond.com:30080", description = "http 서버"),
                @Server(url = "https://specguard-api.beyond.com:30443", description = "https 서버")
        },
        security = {
                @SecurityRequirement(name = "bearerAuth") // 기본 보안 스키마 적용
        }
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
@SecurityScheme(
        name = "refreshTokenCookie",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.COOKIE,
        paramName = "refreshToken"
)
public class OpenApiConfig {
}
