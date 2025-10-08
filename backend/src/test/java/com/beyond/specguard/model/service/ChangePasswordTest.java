package com.beyond.specguard.model.service;

import com.beyond.specguard.company.common.model.entity.ClientCompany;
import com.beyond.specguard.company.common.model.entity.ClientUser;
import com.beyond.specguard.company.common.model.repository.ClientCompanyRepository;
import com.beyond.specguard.company.common.model.repository.ClientUserRepository;
import com.beyond.specguard.company.management.model.dto.request.ChangePasswordRequestDto;
import com.beyond.specguard.company.management.model.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Rollback(false)

public class ChangePasswordTest {

    @Autowired
    private UserService userService;

    @Autowired
    private ClientUserRepository clientUserRepository;

    @Autowired
    private ClientCompanyRepository clientCompanyRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("비밀번호 수정 테스트")
    void changePassword_user_success() {
        // given
        ClientUser user = clientUserRepository.save(
                ClientUser.builder()
                        .name("김택곤")
                        .email("user@test.com")
                        .passwordHash(passwordEncoder.encode("old1234"))
                        .build()
        );

        ChangePasswordRequestDto dto = ChangePasswordRequestDto.builder()
                .oldPassword("old1234")
                .newPassword("new1234")
                .build();

        // when
        userService.changePassword(user.getId(), dto);

        // then
        ClientUser updated = clientUserRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("new1234", updated.getPasswordHash())).isTrue();
        System.out.println("유저 비밀번호 변경 완료: " + updated.getEmail());
    }
}
