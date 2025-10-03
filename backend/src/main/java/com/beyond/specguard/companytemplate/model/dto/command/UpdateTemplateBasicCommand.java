package com.beyond.specguard.companytemplate.model.dto.command;

import com.beyond.specguard.company.common.model.entity.ClientUser;
import com.beyond.specguard.companytemplate.model.dto.request.CompanyTemplateBasicRequestDto;

import java.util.UUID;

public record UpdateTemplateBasicCommand(UUID templateId, CompanyTemplateBasicRequestDto requestDto,
                                         ClientUser clientUser) {

}
