package com.ruoyi.blog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.blog.dto.BlogApiClientCreateRequest;
import com.ruoyi.blog.dto.BlogApiClientPageQuery;
import com.ruoyi.blog.dto.BlogApiClientStatusRequest;
import com.ruoyi.blog.vo.BlogApiClientSecretVO;
import com.ruoyi.blog.vo.BlogApiClientVO;

public interface BlogApiClientAdminService
{
    Page<BlogApiClientVO> page(BlogApiClientPageQuery query);

    BlogApiClientSecretVO create(BlogApiClientCreateRequest request);

    void updateStatus(Long id, BlogApiClientStatusRequest request);

    BlogApiClientSecretVO rotateSecret(Long id);
}
