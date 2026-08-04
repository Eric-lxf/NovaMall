package com.ruoyi.history.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class HistoryClaimAuditRequest
{
    @NotEmpty(message = "主张ID列表不能为空")
    private List<Long> claimIds;

    /** APPROVE / REJECT */
    @NotBlank(message = "审核动作不能为空")
    private String action;

    private String remark;
}
