package com.ruoyi.blog.service.hn.impl;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.blog.constant.AiModuleCode;
import com.ruoyi.blog.constant.HnBoard;
import com.ruoyi.blog.domain.BlogHnItem;
import com.ruoyi.blog.domain.BlogHnRank;
import com.ruoyi.blog.dto.AiCompletionRequest;
import com.ruoyi.blog.dto.hn.HnItemDto;
import com.ruoyi.blog.mapper.BlogHnItemMapper;
import com.ruoyi.blog.mapper.BlogHnRankMapper;
import com.ruoyi.blog.service.DeepSeekService;
import com.ruoyi.blog.service.hn.HnClient;
import com.ruoyi.blog.service.hn.HnSyncService;
import com.ruoyi.common.exception.ServiceException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class HnSyncServiceImpl implements HnSyncService
{
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final String HN_ITEM_URL_PREFIX = "https://news.ycombinator.com/item?id=";

    private final HnClient hnClient;
    private final BlogHnItemMapper itemMapper;
    private final BlogHnRankMapper rankMapper;
    private final DeepSeekService deepSeekService;
    private final ObjectMapper objectMapper;
    private final HnSyncServiceImpl self;

    private final Set<String> runningBoards = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, LocalDateTime> lastSyncAt = new ConcurrentHashMap<>();

    public HnSyncServiceImpl(HnClient hnClient, BlogHnItemMapper itemMapper, BlogHnRankMapper rankMapper,
            DeepSeekService deepSeekService, ObjectMapper objectMapper, @Lazy HnSyncServiceImpl self)
    {
        this.hnClient = hnClient;
        this.itemMapper = itemMapper;
        this.rankMapper = rankMapper;
        this.deepSeekService = deepSeekService;
        this.objectMapper = objectMapper;
        this.self = self;
    }

    @Override
    public boolean syncBoardAsync(String board)
    {
        HnBoard hnBoard = HnBoard.fromCode(board);
        String code = hnBoard.getCode();
        if (!runningBoards.add(code))
        {
            return false;
        }
        self.runSyncBoardAsync(hnBoard);
        return true;
    }

    @Async("aiTaskExecutor")
    public void runSyncBoardAsync(HnBoard hnBoard)
    {
        String code = hnBoard.getCode();
        try
        {
            doSyncBoardUnlocked(hnBoard);
        }
        finally
        {
            runningBoards.remove(code);
            lastSyncAt.put(code, LocalDateTime.now());
        }
    }

    @Override
    public void syncBoard(String board)
    {
        HnBoard hnBoard = HnBoard.fromCode(board);
        String code = hnBoard.getCode();
        if (!runningBoards.add(code))
        {
            return;
        }
        try
        {
            doSyncBoardUnlocked(hnBoard);
        }
        finally
        {
            runningBoards.remove(code);
            lastSyncAt.put(code, LocalDateTime.now());
        }
    }

    private void doSyncBoardUnlocked(HnBoard hnBoard)
    {
        List<Long> ids = hnClient.fetchStoryIds(hnBoard);
        LocalDateTime snapshotAt = LocalDateTime.now();
        LocalDateTime fetchedAt = snapshotAt;

        for (Long hnId : ids)
        {
            HnItemDto dto;
            try
            {
                dto = hnClient.fetchItem(hnId);
            }
            catch (ServiceException ex)
            {
                log.warn("Fetch HN item failed, hnId={}: {}", hnId, ex.getMessage());
                continue;
            }
            if (dto == null || !StringUtils.hasText(dto.getTitle()))
            {
                continue;
            }
            upsertItem(dto, fetchedAt);
        }

        insertRankSnapshot(hnBoard.getCode(), ids, snapshotAt);
        deleteOldRanks(hnBoard.getCode(), snapshotAt);
        translatePendingInSnapshot(ids);
    }

    @Override
    public void syncAll()
    {
        for (HnBoard board : HnBoard.values())
        {
            syncBoard(board.getCode());
        }
    }

    @Override
    public Map<String, Object> syncStatus()
    {
        Map<String, Object> status = new LinkedHashMap<>();
        for (HnBoard board : HnBoard.values())
        {
            String code = board.getCode();
            Map<String, Object> boardStatus = new HashMap<>();
            boardStatus.put("running", runningBoards.contains(code));
            boardStatus.put("lastSyncAt", lastSyncAt.get(code));
            status.put(code, boardStatus);
        }
        return status;
    }

    private void upsertItem(HnItemDto dto, LocalDateTime fetchedAt)
    {
        BlogHnItem existing = itemMapper.selectOne(new LambdaQueryWrapper<BlogHnItem>()
                .eq(BlogHnItem::getHnId, dto.getId())
                .last("LIMIT 1"));
        boolean isNew = existing == null;
        BlogHnItem item = isNew ? new BlogHnItem() : existing;
        boolean titleChanged = !isNew && !Objects.equals(existing.getTitleEn(), dto.getTitle());

        item.setHnId(dto.getId());
        item.setItemType(dto.getType());
        item.setTitleEn(dto.getTitle());
        item.setUrl(dto.getUrl());
        item.setHnUrl(HN_ITEM_URL_PREFIX + dto.getId());
        item.setTextEn(dto.getText());
        item.setScore(dto.getScore() != null ? dto.getScore() : 0);
        item.setAuthor(dto.getAuthor());
        item.setCommentCount(dto.getDescendants() != null ? dto.getDescendants() : 0);
        if (dto.getTime() != null)
        {
            item.setHnTime(Instant.ofEpochSecond(dto.getTime()).atZone(SHANGHAI).toLocalDateTime());
        }
        item.setFetchedAt(fetchedAt);

        if (isNew || titleChanged)
        {
            item.setTranslateStatus("pending");
        }

        LocalDateTime now = LocalDateTime.now();
        if (isNew)
        {
            item.setCreateTime(now);
            itemMapper.insert(item);
        }
        else
        {
            item.setUpdateTime(now);
            itemMapper.updateById(item);
        }
    }

    private void insertRankSnapshot(String board, List<Long> ids, LocalDateTime snapshotAt)
    {
        for (int i = 0; i < ids.size(); i++)
        {
            BlogHnRank rank = new BlogHnRank();
            rank.setBoard(board);
            rank.setHnId(ids.get(i));
            rank.setRank(i + 1);
            rank.setSnapshotAt(snapshotAt);
            rankMapper.insert(rank);
        }
    }

    private void deleteOldRanks(String board, LocalDateTime snapshotAt)
    {
        rankMapper.delete(new LambdaQueryWrapper<BlogHnRank>()
                .eq(BlogHnRank::getBoard, board)
                .lt(BlogHnRank::getSnapshotAt, snapshotAt));
    }

    private void translatePendingInSnapshot(List<Long> hnIds)
    {
        if (hnIds == null || hnIds.isEmpty())
        {
            return;
        }
        List<BlogHnItem> items = itemMapper.selectList(new LambdaQueryWrapper<BlogHnItem>()
                .in(BlogHnItem::getHnId, hnIds)
                .in(BlogHnItem::getTranslateStatus, List.of("pending", "fail")));
        for (BlogHnItem item : items)
        {
            translateItem(item);
        }
    }

    private void translateItem(BlogHnItem item)
    {
        try
        {
            StringBuilder prompt = new StringBuilder();
            prompt.append("title_en: ").append(item.getTitleEn());
            if (StringUtils.hasText(item.getTextEn()))
            {
                prompt.append("\ntext_en: ").append(item.getTextEn());
            }

            AiCompletionRequest request = new AiCompletionRequest();
            request.setScene("TRANSLATE");
            request.setPrompt(prompt.toString());
            String raw = deepSeekService.chatCompletion(request, AiModuleCode.WRITE);
            JsonNode node = parseJson(raw);

            String titleZh = node.path("title_zh").asText("");
            if (!StringUtils.hasText(titleZh))
            {
                throw new ServiceException("翻译结果缺少 title_zh");
            }

            item.setTitleZh(titleZh);
            item.setSummaryZh(node.path("summary_zh").asText(""));
            item.setTextZh(node.path("text_zh").asText(""));
            item.setTranslateStatus("ok");
            item.setTranslatedAt(LocalDateTime.now());
            item.setStatus(1);
            item.setUpdateTime(LocalDateTime.now());
            itemMapper.updateById(item);
        }
        catch (Exception ex)
        {
            log.warn("HN translate failed, hnId={}: {}", item.getHnId(), ex.getMessage());
            item.setTranslateStatus("fail");
            item.setUpdateTime(LocalDateTime.now());
            itemMapper.updateById(item);
        }
    }

    private JsonNode parseJson(String raw)
    {
        try
        {
            String trimmed = raw == null ? "{}" : raw.trim();
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start)
            {
                trimmed = trimmed.substring(start, end + 1);
            }
            return objectMapper.readTree(trimmed);
        }
        catch (Exception ex)
        {
            return objectMapper.createObjectNode();
        }
    }
}
