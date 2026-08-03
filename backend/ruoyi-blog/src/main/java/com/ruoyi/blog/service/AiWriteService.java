package com.ruoyi.blog.service;

import com.ruoyi.blog.dto.AiWriteWizardRequest;

public interface AiWriteService
{

    Long submitGenerateTitles(AiWriteWizardRequest request);

    Long submitGenerateSummary(AiWriteWizardRequest request);

    Long submitGenerateOutline(AiWriteWizardRequest request);

    Long submitGenerateArticle(AiWriteWizardRequest request);
}
