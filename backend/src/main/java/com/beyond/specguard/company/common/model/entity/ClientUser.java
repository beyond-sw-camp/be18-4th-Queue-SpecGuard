package com.beyond.specguard.company.common.model.entity;

import com.beyond.specguard.auth.exception.errorcode.AuthErrorCode;
import com.beyond.specguard.company.management.model.dto.request.UpdateUserRequestDto;
import com.beyond.specguard.common.exception.CustomException;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "client_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ClientUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;

    // FK 조건 서비스단에서 검증하도록 수정
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "company_id",
            nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT) // FK 제약 제거
    )
    private ClientCompany company;

    // 사용자 이름 (nullable 허용 → 소셜 로그인 시 내려줄 수도 있음)
    @Column(length = 100, nullable = true)
    private String name;

    // 초대 이메일 = 로그인 이메일 (엄격 모드 기준, UNIQUE)
    @Column(length = 255, nullable = false, unique = true)
    private String email;

    // 로컬 로그인일 경우에만 값 존재
    @Column(name = "password_hash", length = 64, nullable = true)
    private String passwordHash;

    @Column(length = 50)
    private String phone;

    // 권한 (OWNER / MANAGER / VIEWER)
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Role role;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;


    // 소셜 로그인용
    @Column(length = 20, nullable = false)
    private String provider;   // local / google / naver

    @Column(name = "provider_id", length = 100, nullable = true)
    private String providerId; // 소셜 계정 고유 ID (구글 sub, 네이버 id)

    @Column(name = "profile_image", length = 500, nullable = true)
    private String profileImage;


    public enum Role {
        OWNER, MANAGER, VIEWER
    }

    public void update(UpdateUserRequestDto dto) {
        if (dto.getName() != null) {
            this.name = dto.getName();
        }
        if (dto.getPhone() != null) {
            this.phone = dto.getPhone();
        }
    }
    public void changePassword(String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            throw new CustomException(AuthErrorCode.INVALID_PASSWORD);
        }
        this.passwordHash = encodedPassword;
    }
}
