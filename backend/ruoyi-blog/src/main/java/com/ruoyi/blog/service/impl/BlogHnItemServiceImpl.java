package com.ruoyi.blog.service.impl;

import java.time.LocalDateTime;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.blog.constant.HnBoard;
import com.ruoyi.blog.domain.BlogHnItem;
import com.ruoyi.blog.dto.HnItemAdminQuery;
import com.ruoyi.blog.mapper.BlogHnItemMapper;
import com.ruoyi.blog.mapper.BlogHnRankMapper;
import com.ruoyi.blog.service.BlogHnItemService;
import com.ruoyi.blog.vo.BlogHnItemVO;
import com.ruoyi.blog.vo.BlogHnListItemVO;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BlogHnItemServiceImpl implements BlogHnItemService
{

    private static final int STATUS_PUBLISHED = 1;

    private final BlogHnItemMapper itemMapper;
    private final BlogHnRankMapper rankMapper;

    @Override
    public Page<BlogHnListItemVO> adminPage(HnItemAdminQuery query)
    {
        if (StringUtils.hasText(query.getBoard()))
        {
            HnBoard.fromCode(query.getBoard());
            LocalDateTime snapshotAt = rankMapper.selectMaxSnapshotAt(query.getBoard());
            if (snapshotAt == null)
            {
                return emptyPage(query.getPageNum(), query.getPageSize());
            }
            Page<BlogHnListItemVO> page = new Page<>(query.getPageNum(), query.getPageSize());
            return itemMapper.selectAdminBoardPage(page, query.getBoard(), snapshotAt, query.getKeyword(),
                    query.getTranslateStatus());
        }
        return queryItemsWithoutBoard(query);
    }

    @Override
    public BlogHnItemVO getById(Long id)
    {
        BlogHnItem item = requireItem(id);
        return toDetailVO(item);
    }

    @Override
    public Page<BlogHnListItemVO> publicBoardPage(String board, Integer pageNum, Integer pageSize)
    {
        HnBoard.fromCode(board);
        LocalDateTime snapshotAt = rankMapper.selectMaxSnapshotAt(board);
        if (snapshotAt == null)
        {
            return emptyPage(pageNum, pageSize);
        }
        Page<BlogHnListItemVO> page = new Page<>(pageNum, pageSize);
        Page<BlogHnListItemVO> result = itemMapper.selectPublicBoardPage(page, board, snapshotAt);
        result.setRecords(result.getRecords().stream().map(this::toPublicListVO).toList());
        return result;
    }

    @Override
    public BlogHnItemVO getPublishedByHnId(Long hnId)
    {
        BlogHnItem item = itemMapper.selectOne(new LambdaQueryWrapper<BlogHnItem>()
                .eq(BlogHnItem::getHnId, hnId)
                .last("LIMIT 1"));
        if (item == null || !isPublished(item))
        {
            throw new ServiceException("资源不存在", HttpStatus.NOT_FOUND);
        }
        return toPublicDetailVO(item);
    }

    private Page<BlogHnListItemVO> queryItemsWithoutBoard(HnItemAdminQuery query)
    {
        LambdaQueryWrapper<BlogHnItem> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword()))
        {
            wrapper.and(w -> w.like(BlogHnItem::getTitleEn, query.getKeyword())
                    .or()
                    .like(BlogHnItem::getTitleZh, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getTranslateStatus()))
        {
            wrapper.eq(BlogHnItem::getTranslateStatus, query.getTranslateStatus());
        }
        wrapper.orderByDesc(BlogHnItem::getHnTime);
        Page<BlogHnItem> raw = itemMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        Page<BlogHnListItemVO> voPage = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        voPage.setRecords(raw.getRecords().stream().map(this::toListVO).toList());
        return voPage;
    }

    private BlogHnItem requireItem(Long id)
    {
        BlogHnItem item = itemMapper.selectById(id);
        if (item == null)
        {
            throw new ServiceException("资源不存在", HttpStatus.NOT_FOUND);
        }
        return item;
    }

    private boolean isPublished(BlogHnItem item)
    {
        return item.getStatus() != null
                && item.getStatus() == STATUS_PUBLISHED
                && StringUtils.hasText(item.getTitleZh());
    }

    private BlogHnItemVO toDetailVO(BlogHnItem item)
    {
        BlogHnItemVO vo = new BlogHnItemVO();
        BeanUtils.copyProperties(item, vo);
        return vo;
    }

    private BlogHnItemVO toPublicDetailVO(BlogHnItem item)
    {
        BlogHnItemVO vo = new BlogHnItemVO();
        vo.setHnId(item.getHnId());
        vo.setTitleZh(item.getTitleZh());
        vo.setSummaryZh(item.getSummaryZh());
        vo.setTextZh(item.getTextZh());
        vo.setTitleEn(item.getTitleEn());
        vo.setUrl(item.getUrl());
        vo.setHnUrl(item.getHnUrl());
        vo.setScore(item.getScore());
        vo.setAuthor(item.getAuthor());
        vo.setCommentCount(item.getCommentCount());
        vo.setHnTime(item.getHnTime());
        return vo;
    }

    private BlogHnListItemVO toPublicListVO(BlogHnListItemVO row)
    {
        BlogHnListItemVO vo = new BlogHnListItemVO();
        vo.setHnId(row.getHnId());
        vo.setTitleZh(row.getTitleZh());
        vo.setSummaryZh(row.getSummaryZh());
        vo.setTitleEn(row.getTitleEn());
        vo.setUrl(row.getUrl());
        vo.setHnUrl(row.getHnUrl());
        vo.setScore(row.getScore());
        vo.setAuthor(row.getAuthor());
        vo.setCommentCount(row.getCommentCount());
        vo.setHnTime(row.getHnTime());
        vo.setRank(row.getRank());
        return vo;
    }

    private BlogHnListItemVO toListVO(BlogHnItem item)
    {
        BlogHnListItemVO vo = new BlogHnListItemVO();
        vo.setId(item.getId());
        vo.setHnId(item.getHnId());
        vo.setTitleEn(item.getTitleEn());
        vo.setTitleZh(item.getTitleZh());
        vo.setSummaryZh(item.getSummaryZh());
        vo.setScore(item.getScore());
        vo.setAuthor(item.getAuthor());
        vo.setCommentCount(item.getCommentCount());
        vo.setHnTime(item.getHnTime());
        vo.setTranslateStatus(item.getTranslateStatus());
        vo.setStatus(item.getStatus());
        return vo;
    }

    private Page<BlogHnListItemVO> emptyPage(Integer pageNum, Integer pageSize)
    {
        Page<BlogHnListItemVO> page = new Page<>(pageNum, pageSize);
        page.setTotal(0);
        return page;
    }
}
