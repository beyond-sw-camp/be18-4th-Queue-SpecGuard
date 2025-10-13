package com.beyond.specguard.resume.model.service;

import com.beyond.specguard.common.exception.CustomException;
import com.beyond.specguard.companytemplate.model.entity.CompanyTemplate;
import com.beyond.specguard.companytemplate.model.repository.CompanyTemplateRepository;
import com.beyond.specguard.resume.exception.errorcode.ResumeErrorCode;
import com.beyond.specguard.resume.model.dto.request.ResumeCreateRequest;
import com.beyond.specguard.resume.model.dto.response.ResumeResponse;
import com.beyond.specguard.resume.model.entity.Resume;
import com.beyond.specguard.resume.model.repository.ResumeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ResumeServiceTest {

    @Mock private ResumeRepository resumeRepository;
    @Mock private CompanyTemplateRepository companyTemplateRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private ResumeService resumeService;

    @DisplayName("✅ 이력서 생성 - 정상 입력 시 저장 성공")
    @Test
    void createSuccess() {
        // given
        UUID templateId = UUID.randomUUID();

        ResumeCreateRequest req = new ResumeCreateRequest(
                templateId,
                "홍길동",
                "01012345678",
                "test@specguard.com",
                "1234"
        );

        CompanyTemplate templateMock = mock(CompanyTemplate.class);
        Resume saved = mock(Resume.class);

        // 🔹 Mock 동작 설정
        given(saved.getEmail()).willReturn("test@specguard.com");
        given(saved.getTemplate()).willReturn(templateMock);
        given(templateMock.getId()).willReturn(templateId);

        given(companyTemplateRepository.findById(templateId)).willReturn(Optional.of(templateMock));
        given(resumeRepository.existsByEmailAndTemplateId(req.email(), templateId)).willReturn(false);
        given(passwordEncoder.encode(anyString())).willReturn("encoded_pw");
        given(resumeRepository.saveAndFlush(any(Resume.class))).willReturn(saved);

        // when
        ResumeResponse result = resumeService.create(req);

        // then
        assertThat(result).isNotNull();
        verify(resumeRepository, times(1)).saveAndFlush(any(Resume.class));
        verify(passwordEncoder, times(1)).encode(anyString());
    }

    @DisplayName("❌ 이력서 생성 - 중복 이메일이면 예외 발생")
    @Test
    void createDuplicateEmailThrows() {
        // given
        UUID templateId = UUID.randomUUID();
        ResumeCreateRequest req = new ResumeCreateRequest(
                templateId,
                "홍길동",
                "01012345678",
                "dup@specguard.com",
                "pw"
        );

        CompanyTemplate templateMock = mock(CompanyTemplate.class);
        given(companyTemplateRepository.findById(templateId))
                .willReturn(Optional.of(templateMock));

        given(resumeRepository.existsByEmailAndTemplateId(req.email(), templateId)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> resumeService.create(req))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("해당 이메일은 이미 사용중입니다.");
    }

    @DisplayName("❌ 이력서 생성 - 템플릿 ID가 존재하지 않으면 예외 발생")
    @Test
    void createTemplateNotFoundThrows() {
        // given
        UUID templateId = UUID.randomUUID();
        ResumeCreateRequest req = new ResumeCreateRequest(
                templateId,
                "홍길동",
                "01012345678",
                "test@specguard.com",
                "1234"
        );

        given(companyTemplateRepository.findById(templateId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> resumeService.create(req))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("해당 템플릿을 찾을 수 없습니다.");
    }
}
