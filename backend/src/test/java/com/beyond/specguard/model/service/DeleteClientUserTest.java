package com.beyond.specguard.model.service;

import com.beyond.specguard.company.common.model.entity.ClientCompany;
import com.beyond.specguard.company.common.model.entity.ClientUser;
import com.beyond.specguard.company.common.model.repository.ClientCompanyRepository;
import com.beyond.specguard.company.common.model.repository.ClientUserRepository;
import com.beyond.specguard.company.management.model.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Rollback(false)
public class DeleteClientUserTest {

    @Autowired
    private UserService userService;

    @Autowired
    private ClientUserRepository clientUserRepository;

    @Autowired
    private ClientCompanyRepository clientCompanyRepository;

    @Test
    @DisplayName("유저 삭제 통합 테스트")
    void deleteClientUser_success() {
        // given
        ClientCompany company = clientCompanyRepository.save(
                ClientCompany.builder()
                        .name("Beyond Systems")
                        .build()
        );

        ClientUser user = clientUserRepository.save(
                ClientUser.builder()
                        .name("김택곤")
                        .email("test@beyond.com")
                        .company(company)
                        .build()
        );

        // when
        userService.deleteMyAccount(user.getId());

        // then
        Optional<ClientUser> deleted = clientUserRepository.findById(user.getId());
        assertThat(deleted).isEmpty();

        System.out.println("유저 삭제 완료: " + user.getEmail());
    }
}
