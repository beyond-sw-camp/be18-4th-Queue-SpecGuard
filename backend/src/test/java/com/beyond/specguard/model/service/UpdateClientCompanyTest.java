package com.beyond.specguard.model.service;

import com.beyond.specguard.company.common.model.entity.ClientCompany;
import com.beyond.specguard.company.common.model.repository.ClientCompanyRepository;
import com.beyond.specguard.company.management.model.dto.request.UpdateCompanyRequestDto;
import com.beyond.specguard.company.management.model.service.CompanyService;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Rollback(false)
public class UpdateClientCompanyTest {

    @Autowired
    private CompanyService companyService;

    @Autowired
    private ClientCompanyRepository clientCompanyRepository;

    @Test
    @DisplayName("회사 정보 수정 통합 테스트")
    void updateCompany_success() {
        // given
        ClientCompany company = clientCompanyRepository.save(
                ClientCompany.builder()
                        .name("Beyond Systems")
                        .contactEmail("test@test.com")
                        .contactMobile("01012345678")
                        .build()
        );

        UpdateCompanyRequestDto dto = UpdateCompanyRequestDto.builder()
                .name("Beyond AI")
                .contactEmail("update@test.com")
                .contactMobile("01099998888")
                .build();

        // when
        companyService.updateCompany("beyond", dto, company.getId());

        // then
        ClientCompany updated = clientCompanyRepository.findById(company.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Beyond AI");
        assertThat(updated.getContactEmail()).isEqualTo("update@test.com");
        assertThat(updated.getContactMobile()).isEqualTo("01099998888");

        System.out.println("수정 완료: " + updated.getName() + " | " + updated.getContactEmail() + " | " + updated.getContactMobile());
    }
}
