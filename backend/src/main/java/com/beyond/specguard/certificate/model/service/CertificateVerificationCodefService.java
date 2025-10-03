package com.beyond.specguard.certificate.model.service;

import com.beyond.specguard.certificate.model.dto.CodefVerificationRequest;
import com.beyond.specguard.certificate.model.dto.CodefVerificationResponse;
import com.beyond.specguard.certificate.model.entity.CertificateVerification;
import com.beyond.specguard.certificate.model.repository.CertificateVerificationRepository;
import com.beyond.specguard.certificate.util.CertificateNumberUtil;
import com.beyond.specguard.resume.model.entity.ResumeCertificate;
import com.beyond.specguard.resume.model.repository.ResumeCertificateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateVerificationCodefService implements CertificateVerificationService {

    private final CodefClient codefClient;
    private final CertificateVerificationRepository verificationRepository;
    private final ResumeCertificateRepository resumeCertificateRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void verifyCertificateAsync(UUID resumeId) {
        log.info("[CertificateVerification] 시작 - resumeId={}", resumeId);
        List<ResumeCertificate> resumeCertificates = resumeCertificateRepository
                .findAllByResume_Id(resumeId);

        // 자격증 순회
        for (ResumeCertificate certificate : resumeCertificates) {
            String certNumber = certificate.getCertificateNumber();

            // 번호가 null
            if (certNumber == null || certNumber.isBlank()) {
                CertificateVerification verification = CertificateVerification.builder()
                        .verificationSource("CODEF")
                        .resumeCertificate(certificate)
                        .status(CertificateVerification.Status.NOTEXISTED)
                        .build();
                verificationRepository.save(verification);
                log.info("[CertificateVerification] certNumber 없음 → NOTEXISTED 저장 - resumeId={}, certId={}",
                        resumeId, certificate.getId());
                continue;
            }
            log.info("[CertificateVerification] 자격증 검증 시작 - resumeId={}, certName={}, certNumber={}",
                    resumeId,
                    certificate.getCertificateName(),
                    certificate.getCertificateNumber());

            CertificateVerification verification = CertificateVerification.builder()
                    .verificationSource("CODEF")
                    .resumeCertificate(certificate)
                    .build();
            try {

                log.debug("name : {}, number : {}", certificate.getResume().getName(), CertificateNumberUtil.preprocessCertificateNumber(certificate.getCertificateNumber()));
                // 요청 DTO 구성
                CodefVerificationRequest request = CodefVerificationRequest.builder()
                        .userName(certificate.getResume().getName())
                        .docNo(CertificateNumberUtil.preprocessCertificateNumber(certificate.getCertificateNumber()))
                        .build();

                // API 호출
                CodefVerificationResponse response = codefClient.verifyCertificate(request);
                log.info("[CertificateVerification] CODEF API 응답 수신 - resumeId={}, resIssueYN={}, resultDesc={}",
                        resumeId,
                        response.getData().getResIssueYN(),
                        response.getData().getResResultDesc());
                verification.setVerifiedNow();

                // 성공 여부 판별
                String resIssueYN = response.getData().getResIssueYN();

                if ("1".equals(resIssueYN)) {
                    verification.setStatusSuccess();
                    log.info("[CertificateVerification] 검증 성공 - resumeId={}, certName={}", resumeId, certificate.getCertificateName());
                } else {
                    verification.setStatusFailed();
                    verification.setErrorMessage(response.getData().getResResultDesc());
                    log.warn("[CertificateVerification] 검증 실패 - resumeId={}, certName={}, reason={}",
                            resumeId,
                            certificate.getCertificateName(),
                            response.getData().getResResultDesc());
                }

            } catch (Exception e) {
                verification.setStatusFailed();
                verification.setErrorMessage(e.getMessage());
                log.error("[CertificateVerification] 예외 발생 - resumeId={}, certName={}, error={}",
                        resumeId,
                        certificate.getCertificateName(),
                        e.getMessage(), e);
            } finally {
                verificationRepository.save(verification);
                log.info("[CertificateVerification] 검증 결과 저장 완료 - resumeId={}, certName={}",
                        resumeId,
                        certificate.getCertificateName());
            }
        }
    }
}
