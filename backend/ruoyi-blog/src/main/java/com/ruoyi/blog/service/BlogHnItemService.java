package com.ruoyi.blog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.blog.dto.HnItemAdminQuery;
import com.ruoyi.blog.vo.BlogHnItemVO;
import com.ruoyi.blog.vo.BlogHnListItemVO;

public interface BlogHnItemService
{

    Page<BlogHnListItemVO> adminPage(HnItemAdminQuery query);

    BlogHnItemVO getById(Long id);

    Page<BlogHnListItemVO> publicBoardPage(String board, Integer pageNum, Integer pageSize);

    BlogHnItemVO getPublishedByHnId(Long hnId);
}
