package com.beyond.specguard.resume.model.service;

import com.beyond.specguard.common.exception.CustomException;
import com.beyond.specguard.common.exception.errorcode.CommonErrorCode;
import com.beyond.specguard.company.common.model.entity.ClientUser;
import com.beyond.specguard.companytemplate.exception.ErrorCode.CompanyTemplateErrorCode;
import com.beyond.specguard.companytemplate.model.entity.CompanyTemplate;
import com.beyond.specguard.companytemplate.model.entity.CompanyTemplateField;
import com.beyond.specguard.companytemplate.model.repository.CompanyTemplateFieldRepository;
import com.beyond.specguard.companytemplate.model.repository.CompanyTemplateRepository;
import com.beyond.specguard.crawling.dto.GitMetadataResponse;
import com.beyond.specguard.crawling.service.GitHubMetadataService;
import com.beyond.specguard.event.ResumeSubmittedEvent;
import com.beyond.specguard.resume.exception.errorcode.ResumeErrorCode;
import com.beyond.specguard.resume.model.dto.request.CompanyTemplateResponseDraftUpsertRequest;
import com.beyond.specguard.resume.model.dto.request.ResumeAggregateUpdateRequest;
import com.beyond.specguard.resume.model.dto.request.ResumeBasicCreateRequest;
import com.beyond.specguard.resume.model.dto.request.ResumeCertificateUpsertRequest;
import com.beyond.specguard.resume.model.dto.request.ResumeCreateRequest;
import com.beyond.specguard.resume.model.dto.request.ResumeEducationUpsertRequest;
import com.beyond.specguard.resume.model.dto.request.ResumeExperienceUpsertRequest;
import com.beyond.specguard.resume.model.dto.response.*;
import com.beyond.specguard.resume.model.entity.*;
import com.beyond.specguard.resume.model.repository.*;
import com.beyond.specguard.resume.model.spec.ResumeSpecification;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {
    private final ResumeRepository resumeRepository;
    private final ResumeBasicRepository basicRepository;
    private final ResumeEducationRepository educationRepository;
    private final ResumeExperienceRepository experienceRepository;
    private final ResumeCertificateRepository certificateRepository;
    private final ResumeLinkRepository linkRepository;
    private final CompanyTemplateResponseRepository templateResponseRepository;
    private final CompanyFormSubmissionRepository submissionRepository;
    private final CompanyTemplateRepository companyTemplateRepository;
    private final PasswordEncoder passwordEncoder;
    private final LocalFileStorageService storageService;
    private final ApplicationEventPublisher eventPublisher;
    private final CompanyTemplateFieldRepository companyTemplateFieldRepository;
    private final GitHubMetadataService gitHubMetadataService;
    private final CompanyTemplateResponseAnalysisRepository analysisRepository;

    //이력서 생성에서 create
    @Transactional
    public ResumeResponse create(ResumeCreateRequest req) {
        CompanyTemplate companyTemplate = companyTemplateRepository.findById(req.templateId())
                .orElseThrow(() -> new CustomException(CompanyTemplateErrorCode.TEMPLATE_NOT_FOUND));

        if (resumeRepository.existsByEmailAndTemplateId(req.email(), req.templateId())) {
            throw new CustomException(ResumeErrorCode.DUPLICATE_EMAIL);
        }

        Resume r = req.toEntity(companyTemplate);

        r.encodePassword(passwordEncoder.encode(req.password().trim()));

        Resume saved = resumeRepository.saveAndFlush(r);

        return ResumeResponse.fromEntity(saved);
    }

    private void validateOwnerShip(Resume resume, String username, UUID templateId) {
        if (!resume.getEmail().equals(username) || !resume.getTemplate().getId().equals(templateId)) {
            throw new CustomException(ResumeErrorCode.ACCESS_DENIED);
        }
    }

    //지원서 단건 조회에서 get
    @Transactional(readOnly = true)
    public ResumeResponse get(UUID resumeId, String username, UUID templateId) {
        Resume resume = resumeRepository.findById(resumeId)
                        .orElseThrow(() -> new CustomException(ResumeErrorCode.RESUME_NOT_FOUND));

        validateOwnerShip(resume, username, templateId);

        return ResumeResponse.fromEntity(resume);
    }

    @Transactional
    public Map<String, Object> loginAndGetResume(UUID resumeId) {
        // Transactional 안에서 조회해야 연관 엔티티들 채워줌
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new CustomException(ResumeErrorCode.RESUME_NOT_FOUND));

        return Map.of(
                "message", "Login successful",
                "resume", ResumeResponse.fromEntity(resume)
        );
    }

    //지원서 목록 조회에서 list
    @Transactional(readOnly = true)
    public ResumeListResponseDto list(UUID templateId, Pageable pageable, ClientUser clientUser, Resume.ResumeStatus status) {
        UUID companyId = clientUser.getCompany().getId();

        Specification<Resume> spec = Specification.allOf(
                ResumeSpecification.hasCompany(companyId),
                ResumeSpecification.hasTemplate(templateId),
                ResumeSpecification.hasStatus(status)
        );

        long totalElements = resumeRepository.count(spec);

        Page<Resume> page = resumeRepository.findAll(spec, pageable);

        Page<ResumeListResponseDto.Item> mapped = page.map(ResumeListResponseDto.Item::fromEntity);

        return ResumeListResponseDto.builder()
                .totalElements(totalElements)
                .totalPages(mapped.getTotalPages())
                .pageNumber(mapped.getNumber())
                .pageSize(mapped.getSize())
                .contents(mapped.getContent())
                .build();
    }



    // 학력 중복/기간 검증
    private void validateEducationDuplicates(List<ResumeEducationUpsertRequest> educations) {
        Set<String> keys = new HashSet<>();
        for (var edu : educations) {
            if (edu.startDate() != null && edu.endDate() != null && edu.startDate().isAfter(edu.endDate())) {
                throw new CustomException(ResumeErrorCode.INVALID_REQUEST);
            }
            String key = edu.schoolName() + "|" + edu.startDate() + "|" + edu.endDate();
            if (!keys.add(key)) {
                throw new CustomException(ResumeErrorCode.DUPLICATE_ENTRY);
            }
        }
    }

    // 경력 중복/기간 검증
    private void validateExperienceDuplicates(List<ResumeExperienceUpsertRequest> experiences) {
        Set<String> keys = new HashSet<>();
        for (var exp : experiences) {
            if (exp.startDate() != null && exp.endDate() != null && exp.startDate().isAfter(exp.endDate())) {
                throw new CustomException(ResumeErrorCode.INVALID_REQUEST);
            }
            String key = exp.companyName() + "|" + exp.startDate() + "|" + exp.endDate();
            if (!keys.add(key)) {
                throw new CustomException(ResumeErrorCode.DUPLICATE_ENTRY);
            }
        }
    }

    //이력서 기본 정보 UPDATE/INSERT 에서 upsertBasic
    @Transactional
    public ResumeBasicResponse upsertBasic(UUID resumeId, UUID templateId, String email, ResumeBasicCreateRequest req, MultipartFile profileImage) {
        try {
            Resume resume = resumeRepository.findById(resumeId)
                            .orElseThrow(() -> new CustomException(ResumeErrorCode.RESUME_NOT_FOUND));

            validateDraft(resume);

            validateOwnerShip(resume, email, templateId);

            Optional<ResumeBasic> opt = basicRepository.findByResume_Id(resume.getId());

            ResumeBasic basic = opt.orElseGet(() -> basicRepository.saveAndFlush(req.toEntity(resume)));

            // 파일이 있으면 업로드
            if (profileImage != null && !profileImage.isEmpty()) {
                String url = storageService.saveProfileImage(resume.getId(), profileImage);
                basic.changeProfileImageUrl(url); // 엔티티 필드에 URL 저장
            }

            // 수정 경로 (null 이면 변경 없음)
            if (opt.isPresent()) {
                basic.update(req);
            }

            return ResumeBasicResponse.fromEntity(basic);

        } catch (MaxUploadSizeExceededException e) {
            throw new CustomException(ResumeErrorCode.INTERNAL_SERVER_ERROR);
        }
        // TODO: 외부 스토리지 SDK 예외 타입/메시지 기반으로 판단
    }

    //이력서 학력/경력/포트폴리오 링크 정보 UPDATE/INSERT
    @Transactional
    public void upsertAggregate(UUID resumeId, UUID templateId, String email, ResumeAggregateUpdateRequest req) {
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new CustomException(ResumeErrorCode.RESUME_NOT_FOUND));

        validateDraft(resume);

        validateOwnerShip(resume, email, templateId);

        if (req.educations() != null) {
            validateEducationDuplicates(req.educations());

            List<ResumeEducation> updatedFields = new ArrayList<>();

            Map<UUID, ResumeEducationUpsertRequest> dtoMap = req.educations().stream()
                    .filter(f -> f.id() != null)
                    .collect(Collectors.toMap(ResumeEducationUpsertRequest::id, f -> f));

            // 6. 업데이트
            for (ResumeEducation existing : resume.getResumeEducations()) {
                if (dtoMap.containsKey(existing.getId())) {
                    existing.update(dtoMap.get(existing.getId()));
                    updatedFields.add(existing);
                }
            }

            List<ResumeEducation> newResumeEducations = req.educations().stream()
                            .filter(f -> f.id() == null)
                            .map(e -> e.toEntity(resume))
                            .toList();

            resume.getResumeEducations().clear();

            updatedFields.addAll(newResumeEducations);

            resume.getResumeEducations().addAll(updatedFields);
        }

        if (req.experiences() != null) {
            validateExperienceDuplicates(req.experiences());

            List<ResumeExperience> updatedFields = new ArrayList<>();

            Map<UUID, ResumeExperienceUpsertRequest> dtoMap = req.experiences().stream()
                    .filter(f -> f.id() != null)
                    .collect(Collectors.toMap(ResumeExperienceUpsertRequest::id, f -> f));

            // 6. 업데이트
            for (ResumeExperience existing : resume.getResumeExperiences()) {
                if (dtoMap.containsKey(existing.getId())) {
                    // 업데이트
                    existing.update(dtoMap.get(existing.getId()));
                    updatedFields.add(existing);
                }
            }

            List<ResumeExperience> newResumeExperience = req.experiences().stream()
                    .filter(f -> f.id() == null)
                    .map(e -> e.toEntity(resume))
                    .toList();

            resume.getResumeExperiences().clear();

            updatedFields.addAll(newResumeExperience);

            resume.getResumeExperiences().addAll(updatedFields);
        }


        if (req.links() == null || req.links().isEmpty()) {
            // 빈 row를 입력해줌
            List<ResumeLink> defaultLinks = Arrays.asList(
                    ResumeLink.builder().resume(resume).linkType(ResumeLink.LinkType.GITHUB).url(null).build(),
                    ResumeLink.builder().resume(resume).linkType(ResumeLink.LinkType.VELOG).url(null).build(),
                    ResumeLink.builder().resume(resume).linkType(ResumeLink.LinkType.NOTION).url(null).build()
            );

            resume.getResumeLinks().clear();
            resume.getResumeLinks().addAll(defaultLinks);

        } else{

            List<ResumeLink> processed = req.links().stream()
                    .map(l -> ResumeLink.builder()
                            .resume(resume)
                            .linkType(l.linkType())
                            .url((l.url() == null || l.url().trim().isEmpty()) ? null : l.url())
                            .build()
                    ).collect(Collectors.toList());

            Set<ResumeLink.LinkType> providedTypes = processed.stream()
                    .map(ResumeLink::getLinkType)
                    .collect(Collectors.toSet());

            for (ResumeLink.LinkType type : ResumeLink.LinkType.values()) {
                if (!providedTypes.contains(type)) {
                    processed.add(
                            ResumeLink.builder().resume(resume).linkType(type).url(null).build()
                    );
                }
            }

            resume.getResumeLinks().clear();
            resume.getResumeLinks().addAll(processed);
        }

        resumeRepository.saveAndFlush(resume);
    }
    // 중복 자격증 검증
    private void validateResumeCertificate(ResumeCertificateUpsertRequest request) {
        List<ResumeCertificateUpsertRequest.Item> certs = request.certificates();

        Set<String> seen = new HashSet<>();

        for (var d : certs) {
            String key = d.certificateName().trim().toLowerCase() + "|" + d.certificateNumber().trim().toLowerCase();
            if (!seen.add(key)) {
                throw new CustomException(ResumeErrorCode.DUPLICATE_ENTRY);
            }
        }
    }

    //이력서 자격증 정보 UPDATE/INSERT upsertCertificates
    @Transactional
    public void upsertCertificates(UUID resumeId, UUID templateId, String email, ResumeCertificateUpsertRequest req) {
        if(req.certificates() == null || req.certificates().isEmpty()) return;

        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new CustomException(ResumeErrorCode.RESUME_NOT_FOUND));

        validateDraft(resume);

        validateOwnerShip(resume, email, templateId);

        List<ResumeCertificate> updatedFields = new ArrayList<>();

        // 입력이 아예 없으면 -> NULL 값 row 하나 추가
        if (req.certificates().isEmpty()) {
            ResumeCertificate emptyCert = ResumeCertificate.builder()
                    .resume(resume)
                    .certificateName(null)
                    .certificateNumber(null)
                    .issuer(null)
                    .build();

            resume.getResumeCertificates().clear();
            resume.getResumeCertificates().add(emptyCert);
            resumeRepository.saveAndFlush(resume);
            return;
        }


        validateResumeCertificate(req);


        Map<UUID, ResumeCertificateUpsertRequest.Item> dtoMap = req.certificates().stream()
                .filter(f -> f.id() != null)
                .collect(Collectors.toMap(ResumeCertificateUpsertRequest.Item::id, f -> f));


        for (ResumeCertificate existing : resume.getResumeCertificates()) {
            if (dtoMap.containsKey(existing.getId())) {
                existing.update(dtoMap.get(existing.getId()));
                updatedFields.add(existing);
            }
        }


        List<ResumeCertificate> newCertificates = req.certificates().stream()
                .filter(f -> f.id() == null)
                .map(l -> l.toEntity(resume))
                .toList();


        resume.getResumeCertificates().clear();
        updatedFields.addAll(newCertificates);

        resume.getResumeCertificates().addAll(updatedFields);


        resumeRepository.saveAndFlush(resume);
    }



    private void validateTemplateResponseConstraints(CompanyTemplateField field, String value) {
        String fieldName = field.getFieldName();

        // 1. 필수 여부
        if (field.isRequired() && (value == null || value.isBlank())) {
            throw new CustomException(ResumeErrorCode.REQUIRED_FIELD_MISSING);
        }

        // null 값이면 (required 아니면) 더 이상 검사할 필요 없음
        if (value == null || value.isBlank()) return;

        // 2. 타입별 검사
        switch (field.getFieldType()) {
            case TEXT -> {
                if (field.getMinLength() != null && value.length() < field.getMinLength()) {
                    log.debug("{}, {}, 길이가 짧습니다.", value.length(), field.getMinLength());
                    throw new CustomException(ResumeErrorCode.FIELD_CONSTRAINT_VIOLATION);
                }

                if (field.getMaxLength() != null && value.length() > field.getMaxLength()) {
                    log.debug("{}, {}, 길이가 깁니다.", value.length(), field.getMaxLength());
                    throw new CustomException(ResumeErrorCode.FIELD_CONSTRAINT_VIOLATION);
                }
            }
            case NUMBER -> {
                try {
                    int num = Integer.parseInt(value);
                    if (field.getMinLength() != null && num < field.getMinLength()) {
                        log.debug("필드({})는 {} 이상이어야 합니다.", fieldName, field.getMinLength());
                        throw new CustomException(ResumeErrorCode.FIELD_CONSTRAINT_VIOLATION);
                    }
                    if (field.getMaxLength() != null && num > field.getMaxLength()) {
                        log.debug("필드({})는 {} 이하이어야 합니다.", fieldName, field.getMaxLength());
                        throw new CustomException(ResumeErrorCode.FIELD_CONSTRAINT_VIOLATION);
                    }
                } catch (NumberFormatException e) {
                    log.debug("필드({})는 숫자만 입력 가능합니다.", fieldName);
                    throw new CustomException(ResumeErrorCode.FIELD_CONSTRAINT_VIOLATION);
                }
            }
            case DATE -> {
                try {
                    LocalDate.parse(value); // 기본 ISO-8601 (yyyy-MM-dd) 포맷
                } catch (DateTimeParseException e) {
                    log.debug("필드({})는 유효한 날짜 형식이어야 합니다. (yyyy-MM-dd)", fieldName);
                    throw new CustomException(ResumeErrorCode.FIELD_CONSTRAINT_VIOLATION);
                }
            }
            case SELECT -> {
                if (field.getOptions() != null && !field.getOptions().isEmpty()) {
                    try{
                        List<String> options = new ObjectMapper().readValue(
                                field.getOptions(),
                                new TypeReference<>() {
                                }
                        );
                        if (!options.contains(value)) {
                            log.debug("필드({})는 허용된 값만 선택 가능합니다. 입력값: {}", fieldName, value);
                            throw new CustomException(ResumeErrorCode.FIELD_CONSTRAINT_VIOLATION);
                        }
                    } catch (JsonProcessingException e) {
                        log.debug("json parsing error");
                        throw new CustomException(CommonErrorCode.INVALID_REQUEST);
                    }
                }
            }
        }
    }

    @Transactional
    public CompanyTemplateResponseResponse saveTemplateResponses(
            Resume resume,
            UUID templateId,
            String email,
            CompanyTemplateResponseDraftUpsertRequest req
    ) {
        validateOwnerShip(resume, email, templateId);

        validateDraft(resume);

        List<CompanyTemplateResponse> updatedFields = new ArrayList<>();

        // 요청된 response 들을 Map<id, dto>로 변환 (업데이트용)
        Map<UUID, CompanyTemplateResponseDraftUpsertRequest.Item> dtoMap = req.responses().stream()
                .filter(f -> f.id() != null)
                .collect(Collectors.toMap(CompanyTemplateResponseDraftUpsertRequest.Item::id, f -> f));

        // 기존 응답 가져오기
        List<CompanyTemplateResponse> existingResponses = templateResponseRepository.findAllByResume_Id(resume.getId());

        // 업데이트 처리
        for (CompanyTemplateResponse existing : existingResponses) {
            if (dtoMap.containsKey(existing.getId())) {
                CompanyTemplateResponseDraftUpsertRequest.Item dto = dtoMap.get(existing.getId());

                // 필드 검증
                validateTemplateResponseConstraints(existing.getCompanyTemplateField(), dto.answer());

                existing.update(dto); // 값 반영
                updatedFields.add(existing);
            }
        }

        // 신규 응답 처리
        List<CompanyTemplateResponse> newResponses = req.responses().stream()
                .filter(f -> f.id() == null)
                .map(dto -> {
                    var field = companyTemplateFieldRepository.getReferenceById(dto.fieldId());

                    // 필드 검증
                    validateTemplateResponseConstraints(field, dto.answer());

                    return dto.toEntity(resume, field);
                })
                .toList();

        updatedFields.addAll(newResponses);

        // resume 연관관계 업데이트
        resume.setTemplateResponses(updatedFields);

        List<CompanyTemplateResponse> responses = templateResponseRepository.saveAllAndFlush(updatedFields);

        return CompanyTemplateResponseResponse.builder()
                .savedCount(responses.size())
                .responses(responses.stream()
                        .map(CompanyTemplateResponseResponse.Item::fromEntity)
                        .toList())
                .build();
    }

    //최종 제출
    @Transactional
    public ResumeSubmitResponse submit(UUID resumeId) {

        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new CustomException(ResumeErrorCode.RESUME_NOT_FOUND));

        validateDraft(resume);

        if (resume.getResumeBasic() == null) {
            throw new CustomException(ResumeErrorCode.INVALID_REQUEST);
        }

        UUID companyId = resume.getTemplate().getClientCompany().getId();

        if (submissionRepository.existsByResume_IdAndCompanyId(resume.getId(), companyId)) {
            throw new CustomException(ResumeErrorCode.ALREADY_SUBMITTED);
        }

        CompanyFormSubmission submission = submissionRepository.saveAndFlush(
                CompanyFormSubmission.builder()
                        .resume(resume)
                        .companyId(companyId)
                        .build()
        );

        resume.setStatusPending();

        long start = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();

        log.info("[Submit] BEFORE publish ResumeSubmittedEvent - resumeId={}, templateId={}, thread={}, time={}",
                resume.getId(), resume.getTemplate().getId(), threadName, start);
        eventPublisher.publishEvent(
                new ResumeSubmittedEvent(resume.getId(), resume.getTemplate().getId())
        );

/*        log.info("[Submit] BEFORE publish CertificateVerificationEvent - resumeId={}, thread={}, time={}",
                resume.getId(), threadName, System.currentTimeMillis());
        eventPublisher.publishEvent(
                new CertificateVerificationEvent(resume.getId())
        );*/

        resumeRepository.updateStatus(resume.getId(), resume.getStatus());

        return ResumeSubmitResponse.fromEntity(submission);
    }

    private void validateDraft(Resume resume) {
        if (!resume.getStatus().equals(Resume.ResumeStatus.DRAFT)) {
            throw new  CustomException(ResumeErrorCode.ALREADY_SUBMITTED);
        }
    }

    private void cascadeDeleteByResume(UUID resumeId) {
        templateResponseRepository.deleteByResume_Id(resumeId);
        certificateRepository.deleteByResume_Id(resumeId);
        linkRepository.deleteByResume_Id(resumeId);
        experienceRepository.deleteByResume_Id(resumeId);
        educationRepository.deleteByResume_Id(resumeId);
        basicRepository.deleteByResume_Id(resumeId);
        submissionRepository.deleteByResume_Id(resumeId);

        resumeRepository.deleteById(resumeId);
        storageService.deleteAllProfileImages(resumeId);
    }

    //삭제
    @Transactional
    public int cleanupExpiredUnsubmittedResumes(int batchSize) {
        log.info("[cleanup] called with batchSize={}", batchSize);
        int totalDeleted = 0;
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        var expiredTemplateIds = companyTemplateRepository.findExpiredTemplateIds(now);
        log.info("[cleanup] expiredTemplateIds={}", expiredTemplateIds);
        if (expiredTemplateIds.isEmpty()) return 0;

        Pageable limit = PageRequest.of(0, batchSize);

        while (true) {
            var targetIds = resumeRepository.findUnsubmittedIdsByTemplateIds(expiredTemplateIds, limit);
            if (targetIds.isEmpty()) break;

            log.debug("[cleanup] targetIds to delete={}", targetIds);

            for (UUID resumeId : targetIds) {
                // 자식 -> 부모 순으로 삭제 + 파일 정리
                cascadeDeleteByResume(resumeId);
                totalDeleted++;
            }

        }
        log.info("[cleanup] finished. totalDeleted={}", totalDeleted);
        return totalDeleted;
    }

    public ResumeResponse get(UUID resumeId, String email) {
        return ResumeResponse.fromEntity(resumeRepository.findById(resumeId)
                .orElseThrow(() -> new CustomException(ResumeErrorCode.RESUME_NOT_FOUND)));
    }
    @Transactional(readOnly = true)
    public ResumeGitSummaryResponse getWithGit(UUID resumeId, String email) {
        // 기본 이력서 조회는 그대로
        ResumeResponse resume = get(resumeId, email);

        GitMetadataResponse gitMetadata = null;
        try {
            // GitHub 링크가 유효할 때만 외부 호출
            boolean hasGithub = linkRepository.findByResume_Id(resumeId).stream()
                    .anyMatch(l ->
                            l.getLinkType() == ResumeLink.LinkType.GITHUB &&
                                    l.getUrl() != null &&
                                    isValidGithubUrl(l.getUrl())
                    );

            if (hasGithub) {
                gitMetadata = gitHubMetadataService.getLanguageStatsPercentage(resumeId);
            }
        } catch (Exception e) { // CustomException 포함 전부 차단
            log.warn("GitHub API 호출 실패. resumeId={}", resumeId, e);
            // 실패는 비치명. gitMetadata는 null로 둠.
        }

        List<String> summaries = Collections.emptyList();
        try {
            summaries = analysisRepository.findAllByResumeId(resumeId).stream()
                    .map(CompanyTemplateResponseAnalysis::getSummary)
                    .filter(Objects::nonNull) // null 값 제거
                    .toList();
        } catch (Exception e) {
            log.warn("Summary 조회 실패. resumeId={}", resumeId, e);
            // 실패해도 summaries는 빈 리스트로 유지
        }

        return ResumeGitSummaryResponse.builder()
                .resume(resume)
                .gitMetadata(gitMetadata)
                .summaries(summaries)// null 가능
                .build();
    }

    private boolean isValidGithubUrl(String url) {
        return url != null && url.matches("^https?://(www\\.)?github\\.com/[^/]+(/[^/]+)?/?$");
    }
}
