# ===============================
# Runtime Image
# ===============================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 빌드된 jar 복사
COPY /app/build/libs/*.jar app.jar

# 포트 설정 (Spring Boot 기본 8080)
EXPOSE 8080

# 실행
ENTRYPOINT ["java", "-jar", "app.jar"]