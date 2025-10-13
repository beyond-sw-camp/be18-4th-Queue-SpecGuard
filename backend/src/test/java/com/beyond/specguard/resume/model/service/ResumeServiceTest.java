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
        // saveAndFlush()는 중복 저장, 중복 호출을 방지함. => 정확히 한 번만 호출하는 지 점검합니다.
        given(resumeRepository.saveAndFlush(any(Resume.class))).willReturn(saved);

        // when) ResumeResponse가 null이 아닌 지, 정상적으로 엔티티를 반환하는 지 확인.
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

        // existByEmailAndTemplateID()가 true면, 이메일이 중복될 수 있으니까. 막아야 줘야 함.
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
        // ResumeCreateRequest.java 파일 보니까, templateId를 받더라고요. (스펙가드에서는 안 썼던 것 같은데, 잘 모르겠네여)
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
    // 여기부터 get 테스트 구현했어요.
    @DisplayName("✅ 저장 후 조회 시 데이터 일관성 유지 확인")
    @Test
    void createAndGetConsistency() {
        // given
        UUID templateId = UUID.randomUUID();
        ResumeCreateRequest req = new ResumeCreateRequest(
                templateId, "홍길동", "01012345678", "hong@example.com", "pw1234"
        );

        CompanyTemplate template = mock(CompanyTemplate.class);
        Resume resume = mock(Resume.class);
        given(template.getId()).willReturn(templateId);
        given(resume.getEmail()).willReturn("hong@example.com");
        // NullPointerException 방지를 위함.
        given(resume.getTemplate()).willReturn(template);
        given(resumeRepository.findById(any())).willReturn(Optional.of(resume));
        given(resumeRepository.existsByEmailAndTemplateId(req.email(), templateId)).willReturn(false);
        given(companyTemplateRepository.findById(templateId)).willReturn(Optional.of(template));
        given(passwordEncoder.encode(anyString())).willReturn("encoded_pw");
        given(resumeRepository.saveAndFlush(any())).willReturn(resume);

        // when
        ResumeResponse created = resumeService.create(req);
        ResumeResponse fetched = resumeService.get(UUID.randomUUID(), "hong@example.com");

        // then
        assertThat(fetched.email()).isEqualTo(created.email());
        verify(resumeRepository, atLeastOnce()).findById(any());
    }

    @DisplayName("❌ 중복 이메일로 저장 시 트랜잭션 롤백 검증")
    @Test
    void createDuplicateEmailRollback() {
        // given
        UUID templateId = UUID.randomUUID();
        ResumeCreateRequest req = new ResumeCreateRequest(
                templateId, "홍길동", "01012345678", "dup@specguard.com", "pw1234"
        );

        CompanyTemplate template = mock(CompanyTemplate.class);
        given(companyTemplateRepository.findById(templateId)).willReturn(Optional.of(template));

        // 중복 이메일 시도 상황 시뮬레이션
        given(resumeRepository.existsByEmailAndTemplateId(req.email(), templateId)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> resumeService.create(req))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("해당 이메일은 이미 사용중입니다.");

        // 저장 로직이 아예 실행되지 않아야 함
        verify(resumeRepository, never()).saveAndFlush(any());
        verify(passwordEncoder, never()).encode(any());
    }

}
