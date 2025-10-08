package com.beyond.specguard.model.service;

import com.beyond.specguard.company.common.model.entity.ClientCompany;
import com.beyond.specguard.company.common.model.repository.ClientCompanyRepository;
import com.beyond.specguard.company.management.model.service.CompanyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Rollback(false)
public class DeleteClientCompanyTest {
    @Autowired
    private CompanyService companyService;

    @Autowired
    private ClientCompanyRepository clientCompanyRepository;

    @Test
    @DisplayName("회사 삭제 통합 테스트")
    void deleteClientCompany_success() {
        // given
        ClientCompany company = clientCompanyRepository.save(
                ClientCompany.builder()
                        .name("Beyond Systems")
                        .contactEmail("test@beyond.com")
                        .contactMobile("01012345678")
                        .build()
        );

        // when
        companyService.deleteCompany(company.getSlug(), company.getId());

        // then
        Optional<ClientCompany> deleted = clientCompanyRepository.findById(company.getId());
        assertThat(deleted).isEmpty();

        System.out.println("회사 삭제 완료: " + company.getName());
    }
}
