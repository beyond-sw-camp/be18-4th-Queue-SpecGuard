package com.beyond.specguard.model.service;

import com.beyond.specguard.company.common.model.entity.ClientCompany;
import com.beyond.specguard.company.common.model.entity.ClientUser;
import com.beyond.specguard.company.common.model.repository.ClientCompanyRepository;
import com.beyond.specguard.company.common.model.repository.ClientUserRepository;
import com.beyond.specguard.company.management.model.dto.request.UpdateUserRequestDto;
import com.beyond.specguard.company.management.model.service.UserService;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Rollback(false)
public class UpdateClientUserTest {
    @Autowired
    private UserService userService;

    @Autowired
    private ClientCompanyRepository clientCompanyRepository;

    @Autowired
    private ClientUserRepository clientUserRepository;

    @Test
    @DisplayName("유저 정보 수정 통합 테스트")
    void updateUser_success() {
        // given
        ClientCompany company = clientCompanyRepository.save(
                ClientCompany.builder().name("Beyond Systems").build()
        );

        ClientUser user = clientUserRepository.save(
                ClientUser.builder()

                        .name("김택곤")
                        .email("test@beyond.com")
                        .phone("01011112222")
                        .company(company)
                        .build()
        );

        UpdateUserRequestDto dto = UpdateUserRequestDto.builder()
                .name("김태곤")
                .phone("01099998888")
                .build();

        // when
        userService.updateMyInfo(user.getId(), dto);

        // then
        ClientUser updated = clientUserRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("김태곤");
        assertThat(updated.getPhone()).isEqualTo("010-9999-8888");

        System.out.println("수정 완료: " + updated.getName() + ", " + updated.getPhone());
    }
}
