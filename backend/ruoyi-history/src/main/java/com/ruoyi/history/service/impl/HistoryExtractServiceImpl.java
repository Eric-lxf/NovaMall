package com.ruoyi.history.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.blog.constant.AiModuleCode;
import com.ruoyi.blog.dto.AiCompletionRequest;
import com.ruoyi.blog.service.DeepSeekService;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.history.constant.HistoryConstants;
import com.ruoyi.history.domain.HistoryAiTask;
import com.ruoyi.history.domain.HistoryEvent;
import com.ruoyi.history.domain.HistoryEventRelation;
import com.ruoyi.history.domain.HistoryKnowledgeClaim;
import com.ruoyi.history.domain.HistoryPerson;
import com.ruoyi.history.domain.HistoryPlace;
import com.ruoyi.history.domain.HistorySourceDocument;
import com.ruoyi.history.domain.HistorySourceFragment;
import com.ruoyi.history.dto.HistoryClaimAuditRequest;
import com.ruoyi.history.dto.HistoryClaimPageQuery;
import com.ruoyi.history.dto.HistoryExtractRequest;
import com.ruoyi.history.dto.HistorySourceImportResult;
import com.ruoyi.history.dto.extract.HistoryExtractEventDto;
import com.ruoyi.history.dto.extract.HistoryExtractPersonDto;
import com.ruoyi.history.dto.extract.HistoryExtractPlaceDto;
import com.ruoyi.history.dto.extract.HistoryExtractRelationDto;
import com.ruoyi.history.dto.extract.HistoryExtractResultDto;
import com.ruoyi.history.mapper.HistoryAiTaskMapper;
import com.ruoyi.history.mapper.HistoryEventMapper;
import com.ruoyi.history.mapper.HistoryEventRelationMapper;
import com.ruoyi.history.mapper.HistoryKnowledgeClaimMapper;
import com.ruoyi.history.mapper.HistoryPersonMapper;
import com.ruoyi.history.mapper.HistoryPlaceMapper;
import com.ruoyi.history.mapper.HistorySourceDocumentMapper;
import com.ruoyi.history.mapper.HistorySourceFragmentMapper;
import com.ruoyi.history.service.HistoryExtractService;
import com.ruoyi.history.support.HistoryExtractJsonParser;
import com.ruoyi.history.task.HistoryExtractWorker;
import com.ruoyi.history.vo.HistoryClaimVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryExtractServiceImpl implements HistoryExtractService
{
    private static final Set<String> RELATION_TYPES = Set.of(
            "CAUSES", "LEADS_TO", "PART_OF", "HAPPENS_BEFORE", "INFLUENCES", "CONFLICTS_WITH", "SIMILAR_TO");

    private final HistorySourceDocumentMapper historySourceDocumentMapper;
    private final HistorySourceFragmentMapper historySourceFragmentMapper;
    private final HistoryAiTaskMapper historyAiTaskMapper;
    private final HistoryEventMapper historyEventMapper;
    private final HistoryPersonMapper historyPersonMapper;
    private final HistoryPlaceMapper historyPlaceMapper;
    private final HistoryEventRelationMapper historyEventRelationMapper;
    private final HistoryKnowledgeClaimMapper historyKnowledgeClaimMapper;
    private final DeepSeekService deepSeekService;
    private final ObjectProvider<HistoryExtractWorker> historyExtractWorker;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HistorySourceImportResult submitExtract(HistoryExtractRequest request)
    {
        HistorySourceDocument document = requireDocument(request.getDocumentId());
        if (!HistoryConstants.TASK_SUCCEEDED.equals(document.getParseStatus()))
        {
            throw new ServiceException("请先完成资料解析后再抽取", HttpStatus.BAD_REQUEST);
        }
        long fragmentCount = historySourceFragmentMapper.selectCount(new LambdaQueryWrapper<HistorySourceFragment>()
                .eq(HistorySourceFragment::getDocumentId, document.getId()));
        if (fragmentCount <= 0)
        {
            throw new ServiceException("资料尚无文本片段，无法抽取", HttpStatus.BAD_REQUEST);
        }

        String username = SecurityUtils.getUsername();
        HistoryAiTask task = new HistoryAiTask();
        task.setTaskType(HistoryConstants.TASK_TYPE_EXTRACT);
        task.setDocumentId(document.getId());
        task.setStatus(HistoryConstants.TASK_QUEUED);
        task.setInputPayload(JSON.toJSONString(Map.of("documentId", document.getId())));
        task.setRetryCount(0);
        task.setCreateBy(username);
        historyAiTaskMapper.insert(task);

        enqueueAfterCommit(task.getId());

        HistorySourceImportResult result = new HistorySourceImportResult();
        result.setDocumentId(document.getId());
        result.setTaskId(task.getId());
        result.setStatus(task.getStatus());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processExtract(Long taskId)
    {
        HistoryAiTask task = historyAiTaskMapper.selectById(taskId);
        if (task == null || !HistoryConstants.TASK_TYPE_EXTRACT.equals(task.getTaskType()))
        {
            return;
        }
        String status = task.getStatus();
        if (!HistoryConstants.TASK_QUEUED.equals(status)
                && !HistoryConstants.TASK_RETRYING.equals(status)
                && !HistoryConstants.TASK_FAILED.equals(status))
        {
            return;
        }

        markProcessing(task);
        HistorySourceDocument document = historySourceDocumentMapper.selectById(task.getDocumentId());
        if (document == null)
        {
            markFailed(task, "关联资料不存在");
            return;
        }

        try
        {
            List<HistorySourceFragment> fragments = loadFragmentsForPrompt(document.getId());
            if (fragments.isEmpty())
            {
                markFailed(task, "没有可用于抽取的文本片段");
                return;
            }

            String userPrompt = buildUserPrompt(document, fragments);
            AiCompletionRequest completionRequest = new AiCompletionRequest();
            completionRequest.setScene(HistoryConstants.SCENE_HISTORY_EXTRACT);
            completionRequest.setPrompt(userPrompt);
            completionRequest.setTemperature(new BigDecimal("0.2"));

            String raw = deepSeekService.chatCompletion(completionRequest, AiModuleCode.HISTORY_EXTRACT);
            HistoryExtractResultDto extracted = HistoryExtractJsonParser.parse(raw);
            PersistStats stats = persistExtracted(task, document, fragments, extracted, task.getCreateBy());

            Map<String, Object> output = new HashMap<>();
            output.put("eventCount", stats.eventCount);
            output.put("personCount", stats.personCount);
            output.put("placeCount", stats.placeCount);
            output.put("relationCount", stats.relationCount);
            output.put("claimCount", stats.claimCount);
            output.put("rawPreview", abbreviate(raw, 2000));

            HistoryAiTask update = new HistoryAiTask();
            update.setId(task.getId());
            update.setStatus(HistoryConstants.TASK_PENDING_REVIEW);
            update.setProviderCode(AiModuleCode.HISTORY_EXTRACT);
            update.setModelName(HistoryConstants.SCENE_HISTORY_EXTRACT);
            update.setOutputPayload(JSON.toJSONString(output));
            update.setErrorMessage(null);
            update.setFinishedAt(LocalDateTime.now());
            update.setUpdateTime(LocalDateTime.now());
            historyAiTaskMapper.updateById(update);
        }
        catch (Exception ex)
        {
            log.error("extract failed taskId={}", taskId, ex);
            markFailed(task, abbreviate(ex.getMessage(), 1800));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HistorySourceImportResult retryExtract(Long taskId)
    {
        HistoryAiTask task = historyAiTaskMapper.selectById(taskId);
        if (task == null)
        {
            throw new ServiceException("AI 任务不存在", HttpStatus.NOT_FOUND);
        }
        if (!HistoryConstants.TASK_TYPE_EXTRACT.equals(task.getTaskType()))
        {
            throw new ServiceException("仅支持重试抽取任务", HttpStatus.BAD_REQUEST);
        }
        if (!HistoryConstants.TASK_FAILED.equals(task.getStatus())
                && !HistoryConstants.TASK_RETRYING.equals(task.getStatus()))
        {
            throw new ServiceException("仅失败状态的抽取任务可重试", HttpStatus.BAD_REQUEST);
        }
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        if (retryCount >= HistoryConstants.TASK_MAX_RETRY)
        {
            throw new ServiceException("已超过最大重试次数", HttpStatus.BAD_REQUEST);
        }

        HistoryAiTask update = new HistoryAiTask();
        update.setId(task.getId());
        update.setStatus(HistoryConstants.TASK_RETRYING);
        update.setRetryCount(retryCount + 1);
        update.setErrorMessage(null);
        update.setOutputPayload(null);
        update.setStartedAt(null);
        update.setFinishedAt(null);
        update.setUpdateBy(SecurityUtils.getUsername());
        update.setUpdateTime(LocalDateTime.now());
        historyAiTaskMapper.updateById(update);

        enqueueAfterCommit(task.getId());

        HistorySourceImportResult result = new HistorySourceImportResult();
        result.setDocumentId(task.getDocumentId());
        result.setTaskId(task.getId());
        result.setStatus(HistoryConstants.TASK_RETRYING);
        return result;
    }

    @Override
    public Page<HistoryClaimVO> pageClaims(HistoryClaimPageQuery query)
    {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 10 : Math.min(query.getPageSize(), 100);
        LambdaQueryWrapper<HistoryKnowledgeClaim> wrapper = new LambdaQueryWrapper<>();
        if (query.getDocumentId() != null)
        {
            wrapper.eq(HistoryKnowledgeClaim::getDocumentId, query.getDocumentId());
        }
        if (query.getTaskId() != null)
        {
            wrapper.eq(HistoryKnowledgeClaim::getTaskId, query.getTaskId());
        }
        if (StringUtils.hasText(query.getClaimType()))
        {
            wrapper.eq(HistoryKnowledgeClaim::getClaimType, query.getClaimType().trim());
        }
        if (StringUtils.hasText(query.getAuditStatus()))
        {
            wrapper.eq(HistoryKnowledgeClaim::getAuditStatus, query.getAuditStatus().trim());
        }
        wrapper.orderByDesc(HistoryKnowledgeClaim::getId);
        Page<HistoryKnowledgeClaim> page = historyKnowledgeClaimMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<HistoryClaimVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(toClaimVos(page.getRecords()));
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditClaims(HistoryClaimAuditRequest request)
    {
        String action = request.getAction().trim().toUpperCase(Locale.ROOT);
        if (!"APPROVE".equals(action) && !"REJECT".equals(action))
        {
            throw new ServiceException("审核动作仅支持 APPROVE / REJECT", HttpStatus.BAD_REQUEST);
        }
        String username = SecurityUtils.getUsername();
        LocalDateTime now = LocalDateTime.now();
        for (Long claimId : request.getClaimIds())
        {
            HistoryKnowledgeClaim claim = historyKnowledgeClaimMapper.selectById(claimId);
            if (claim == null)
            {
                throw new ServiceException("主张不存在：" + claimId, HttpStatus.NOT_FOUND);
            }
            if (!HistoryConstants.AUDIT_PENDING_REVIEW.equals(claim.getAuditStatus())
                    && !HistoryConstants.AUDIT_DRAFT.equals(claim.getAuditStatus()))
            {
                continue;
            }
            if ("APPROVE".equals(action))
            {
                approveClaim(claim, username, now);
            }
            else
            {
                rejectClaim(claim, username, now, request.getRemark());
            }
        }
    }

    private void approveClaim(HistoryKnowledgeClaim claim, String username, LocalDateTime now)
    {
        HistoryKnowledgeClaim update = new HistoryKnowledgeClaim();
        update.setId(claim.getId());
        update.setAuditStatus(HistoryConstants.AUDIT_PUBLISHED);
        update.setUpdateBy(username);
        update.setUpdateTime(now);
        historyKnowledgeClaimMapper.updateById(update);
        publishTarget(claim.getTargetType(), claim.getTargetId(), username, now);
    }

    private void rejectClaim(HistoryKnowledgeClaim claim, String username, LocalDateTime now, String remark)
    {
        HistoryKnowledgeClaim update = new HistoryKnowledgeClaim();
        update.setId(claim.getId());
        update.setAuditStatus(HistoryConstants.AUDIT_REJECTED);
        if (StringUtils.hasText(remark))
        {
            String note = StringUtils.hasText(claim.getUncertaintyNote())
                    ? claim.getUncertaintyNote() + "；驳回：" + remark
                    : "驳回：" + remark;
            update.setUncertaintyNote(abbreviate(note, 1000));
        }
        update.setUpdateBy(username);
        update.setUpdateTime(now);
        historyKnowledgeClaimMapper.updateById(update);
    }

    private void publishTarget(String targetType, Long targetId, String username, LocalDateTime now)
    {
        if (targetType == null || targetId == null)
        {
            return;
        }
        if (HistoryConstants.TARGET_EVENT.equals(targetType))
        {
            HistoryEvent event = new HistoryEvent();
            event.setId(targetId);
            event.setAuditStatus(HistoryConstants.AUDIT_PUBLISHED);
            event.setUpdateBy(username);
            event.setUpdateTime(now);
            historyEventMapper.updateById(event);
        }
        else if (HistoryConstants.TARGET_PERSON.equals(targetType))
        {
            HistoryPerson person = new HistoryPerson();
            person.setId(targetId);
            person.setAuditStatus(HistoryConstants.AUDIT_PUBLISHED);
            person.setUpdateBy(username);
            person.setUpdateTime(now);
            historyPersonMapper.updateById(person);
        }
        else if (HistoryConstants.TARGET_PLACE.equals(targetType))
        {
            HistoryPlace place = new HistoryPlace();
            place.setId(targetId);
            place.setAuditStatus(HistoryConstants.AUDIT_PUBLISHED);
            place.setUpdateBy(username);
            place.setUpdateTime(now);
            historyPlaceMapper.updateById(place);
        }
        else if (HistoryConstants.TARGET_RELATION.equals(targetType))
        {
            HistoryEventRelation relation = new HistoryEventRelation();
            relation.setId(targetId);
            relation.setAuditStatus(HistoryConstants.AUDIT_PUBLISHED);
            relation.setUpdateBy(username);
            relation.setUpdateTime(now);
            historyEventRelationMapper.updateById(relation);
        }
    }

    private PersistStats persistExtracted(HistoryAiTask task, HistorySourceDocument document,
            List<HistorySourceFragment> fragments, HistoryExtractResultDto extracted, String createBy)
    {
        Map<Integer, HistorySourceFragment> bySeq = fragments.stream()
                .collect(Collectors.toMap(HistorySourceFragment::getSeqNo, f -> f, (a, b) -> a));
        HistorySourceFragment fallback = fragments.get(0);
        Map<String, Long> eventTempIds = new HashMap<>();
        Map<String, Long> placeIdsByName = new HashMap<>();
        PersistStats stats = new PersistStats();
        String username = StringUtils.hasText(createBy) ? createBy : "ai";

        for (HistoryExtractPlaceDto dto : extracted.getPlaces())
        {
            if (!StringUtils.hasText(dto.getName()))
            {
                continue;
            }
            HistoryPlace place = new HistoryPlace();
            place.setName(dto.getName().trim());
            place.setAlias(dto.getAlias());
            place.setModernName(dto.getModernName());
            place.setRegion(dto.getRegion());
            place.setSummary(dto.getSummary());
            place.setAuditStatus(HistoryConstants.AUDIT_PENDING_REVIEW);
            place.setStatus(HistoryConstants.STATUS_NORMAL);
            place.setCreateBy(username);
            historyPlaceMapper.insert(place);
            placeIdsByName.put(place.getName(), place.getId());
            stats.placeCount++;
            stats.claimCount += insertClaimsForTarget(
                    HistoryConstants.CLAIM_TYPE_PLACE,
                    HistoryConstants.TARGET_PLACE,
                    place.getId(),
                    document.getId(),
                    task.getId(),
                    "地点：" + place.getName(),
                    null,
                    dto.getFragmentSeqNos(),
                    bySeq,
                    fallback,
                    username);
        }

        for (HistoryExtractEventDto dto : extracted.getEvents())
        {
            if (!StringUtils.hasText(dto.getTitle()))
            {
                continue;
            }
            HistoryEvent event = new HistoryEvent();
            event.setTitle(dto.getTitle().trim());
            event.setStartYear(dto.getStartYear());
            event.setEndYear(dto.getEndYear());
            event.setDatePrecision(HistoryConstants.DATE_PRECISION_YEAR);
            event.setOriginalDateText(dto.getOriginalDateText());
            event.setCalendarType(StringUtils.hasText(dto.getCalendarType()) ? dto.getCalendarType() : "中国传统纪年");
            event.setIsApproximate(StringUtils.hasText(dto.getUncertaintyNote()));
            event.setSummary(dto.getSummary());
            event.setBackground(dto.getBackground());
            event.setProcess(dto.getProcess());
            event.setCauseAnalysis(dto.getCauseAnalysis());
            event.setImpact(dto.getImpact());
            event.setUncertaintyNote(dto.getUncertaintyNote());
            if (StringUtils.hasText(dto.getPlaceName()))
            {
                Long placeId = placeIdsByName.get(dto.getPlaceName().trim());
                if (placeId == null)
                {
                    HistoryPlace place = new HistoryPlace();
                    place.setName(dto.getPlaceName().trim());
                    place.setAuditStatus(HistoryConstants.AUDIT_PENDING_REVIEW);
                    place.setStatus(HistoryConstants.STATUS_NORMAL);
                    place.setCreateBy(username);
                    historyPlaceMapper.insert(place);
                    placeId = place.getId();
                    placeIdsByName.put(place.getName(), placeId);
                    stats.placeCount++;
                }
                event.setPlaceId(placeId);
            }
            event.setAuditStatus(HistoryConstants.AUDIT_PENDING_REVIEW);
            event.setStatus(HistoryConstants.STATUS_NORMAL);
            event.setCreateBy(username);
            historyEventMapper.insert(event);
            if (StringUtils.hasText(dto.getTempId()))
            {
                eventTempIds.put(dto.getTempId().trim(), event.getId());
            }
            stats.eventCount++;
            stats.claimCount += insertClaimsForTarget(
                    HistoryConstants.CLAIM_TYPE_EVENT,
                    HistoryConstants.TARGET_EVENT,
                    event.getId(),
                    document.getId(),
                    task.getId(),
                    "事件：" + event.getTitle(),
                    dto.getUncertaintyNote(),
                    dto.getFragmentSeqNos(),
                    bySeq,
                    fallback,
                    username);
        }

        for (HistoryExtractPersonDto dto : extracted.getPersons())
        {
            if (!StringUtils.hasText(dto.getName()))
            {
                continue;
            }
            HistoryPerson person = new HistoryPerson();
            person.setName(dto.getName().trim());
            person.setAlias(dto.getAlias());
            person.setBirthYear(dto.getBirthYear());
            person.setDeathYear(dto.getDeathYear());
            person.setDatePrecision(HistoryConstants.DATE_PRECISION_YEAR);
            person.setSummary(dto.getSummary());
            person.setUncertaintyNote(dto.getUncertaintyNote());
            person.setIsApproximate(StringUtils.hasText(dto.getUncertaintyNote()));
            person.setAuditStatus(HistoryConstants.AUDIT_PENDING_REVIEW);
            person.setStatus(HistoryConstants.STATUS_NORMAL);
            person.setCreateBy(username);
            historyPersonMapper.insert(person);
            stats.personCount++;
            stats.claimCount += insertClaimsForTarget(
                    HistoryConstants.CLAIM_TYPE_PERSON,
                    HistoryConstants.TARGET_PERSON,
                    person.getId(),
                    document.getId(),
                    task.getId(),
                    "人物：" + person.getName(),
                    dto.getUncertaintyNote(),
                    dto.getFragmentSeqNos(),
                    bySeq,
                    fallback,
                    username);
        }

        for (HistoryExtractRelationDto dto : extracted.getRelations())
        {
            Long fromId = eventTempIds.get(trimToNull(dto.getFromTempId()));
            Long toId = eventTempIds.get(trimToNull(dto.getToTempId()));
            if (fromId == null || toId == null || Objects.equals(fromId, toId))
            {
                continue;
            }
            String relationType = StringUtils.hasText(dto.getRelationType())
                    ? dto.getRelationType().trim().toUpperCase(Locale.ROOT) : "LEADS_TO";
            if (!RELATION_TYPES.contains(relationType))
            {
                relationType = "LEADS_TO";
            }
            HistoryEventRelation relation = new HistoryEventRelation();
            relation.setFromEventId(fromId);
            relation.setToEventId(toId);
            relation.setRelationType(relationType);
            relation.setDescription(dto.getDescription());
            relation.setAuditStatus(HistoryConstants.AUDIT_PENDING_REVIEW);
            relation.setCreateBy(username);
            historyEventRelationMapper.insert(relation);
            stats.relationCount++;
            stats.claimCount += insertClaimsForTarget(
                    HistoryConstants.CLAIM_TYPE_RELATION,
                    HistoryConstants.TARGET_RELATION,
                    relation.getId(),
                    document.getId(),
                    task.getId(),
                    "关系：" + relationType + " " + fromId + "->" + toId,
                    null,
                    dto.getFragmentSeqNos(),
                    bySeq,
                    fallback,
                    username);
        }

        if (stats.claimCount == 0)
        {
            throw new ServiceException("模型未产出可落库的知识草稿", HttpStatus.ERROR);
        }
        return stats;
    }

    private int insertClaimsForTarget(String claimType, String targetType, Long targetId, Long documentId,
            Long taskId, String claimText, String uncertaintyNote, List<Integer> fragmentSeqNos,
            Map<Integer, HistorySourceFragment> bySeq, HistorySourceFragment fallback, String username)
    {
        Set<Long> fragmentIds = new HashSet<>();
        if (!CollectionUtils.isEmpty(fragmentSeqNos))
        {
            for (Integer seq : fragmentSeqNos)
            {
                if (seq == null)
                {
                    continue;
                }
                HistorySourceFragment fragment = bySeq.get(seq);
                if (fragment != null)
                {
                    fragmentIds.add(fragment.getId());
                }
            }
        }
        if (fragmentIds.isEmpty() && fallback != null)
        {
            fragmentIds.add(fallback.getId());
            if (!StringUtils.hasText(uncertaintyNote))
            {
                uncertaintyNote = "模型未标注来源片段，已回退绑定首个片段，请人工核对";
            }
        }
        int count = 0;
        for (Long fragmentId : fragmentIds)
        {
            HistoryKnowledgeClaim claim = new HistoryKnowledgeClaim();
            claim.setClaimType(claimType);
            claim.setTargetType(targetType);
            claim.setTargetId(targetId);
            claim.setFragmentId(fragmentId);
            claim.setDocumentId(documentId);
            claim.setClaimText(abbreviate(claimText, 2000));
            claim.setUncertaintyNote(uncertaintyNote);
            claim.setAuditStatus(HistoryConstants.AUDIT_PENDING_REVIEW);
            claim.setTaskId(taskId);
            claim.setCreateBy(username);
            historyKnowledgeClaimMapper.insert(claim);
            count++;
        }
        return count;
    }

    private List<HistorySourceFragment> loadFragmentsForPrompt(Long documentId)
    {
        Page<HistorySourceFragment> page = historySourceFragmentMapper.selectPage(
                new Page<>(1, HistoryConstants.EXTRACT_MAX_FRAGMENTS),
                new LambdaQueryWrapper<HistorySourceFragment>()
                        .eq(HistorySourceFragment::getDocumentId, documentId)
                        .orderByAsc(HistorySourceFragment::getSeqNo));
        List<HistorySourceFragment> all = page.getRecords();
        List<HistorySourceFragment> selected = new ArrayList<>();
        int chars = 0;
        for (HistorySourceFragment fragment : all)
        {
            int len = fragment.getContent() == null ? 0 : fragment.getContent().length();
            if (!selected.isEmpty() && chars + len > HistoryConstants.EXTRACT_PROMPT_MAX_CHARS)
            {
                break;
            }
            selected.add(fragment);
            chars += len;
        }
        return selected;
    }

    private String buildUserPrompt(HistorySourceDocument document, List<HistorySourceFragment> fragments)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("资料标题：").append(document.getTitle()).append('\n');
        if (StringUtils.hasText(document.getSourceDesc()))
        {
            sb.append("来源说明：").append(document.getSourceDesc()).append('\n');
        }
        sb.append("以下为编号片段，请抽取并在 fragmentSeqNos 中引用序号：\n\n");
        for (HistorySourceFragment fragment : fragments)
        {
            sb.append("【片段 ").append(fragment.getSeqNo()).append("】");
            if (fragment.getPageNo() != null)
            {
                sb.append(" 页码=").append(fragment.getPageNo());
            }
            if (StringUtils.hasText(fragment.getLocator()))
            {
                sb.append(" 定位=").append(fragment.getLocator());
            }
            sb.append('\n').append(fragment.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    private List<HistoryClaimVO> toClaimVos(List<HistoryKnowledgeClaim> claims)
    {
        if (claims.isEmpty())
        {
            return List.of();
        }
        Set<Long> fragmentIds = claims.stream().map(HistoryKnowledgeClaim::getFragmentId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> documentIds = claims.stream().map(HistoryKnowledgeClaim::getDocumentId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, HistorySourceFragment> fragments = new HashMap<>();
        if (!fragmentIds.isEmpty())
        {
            historySourceFragmentMapper.selectBatchIds(fragmentIds)
                    .forEach(f -> fragments.put(f.getId(), f));
        }
        Map<Long, String> docTitles = new HashMap<>();
        if (!documentIds.isEmpty())
        {
            historySourceDocumentMapper.selectBatchIds(documentIds)
                    .forEach(d -> docTitles.put(d.getId(), d.getTitle()));
        }
        List<HistoryClaimVO> vos = new ArrayList<>(claims.size());
        for (HistoryKnowledgeClaim claim : claims)
        {
            HistoryClaimVO vo = new HistoryClaimVO();
            BeanUtils.copyProperties(claim, vo);
            HistorySourceFragment fragment = claim.getFragmentId() == null ? null : fragments.get(claim.getFragmentId());
            if (fragment != null)
            {
                vo.setFragmentSeqNo(fragment.getSeqNo());
                vo.setFragmentLocator(fragment.getLocator());
                vo.setFragmentPreview(abbreviate(fragment.getContent(), 180));
            }
            vo.setDocumentTitle(claim.getDocumentId() == null ? null : docTitles.get(claim.getDocumentId()));
            vos.add(vo);
        }
        return vos;
    }

    private HistorySourceDocument requireDocument(Long id)
    {
        HistorySourceDocument document = historySourceDocumentMapper.selectById(id);
        if (document == null)
        {
            throw new ServiceException("资料文档不存在", HttpStatus.NOT_FOUND);
        }
        return document;
    }

    private void markProcessing(HistoryAiTask task)
    {
        HistoryAiTask update = new HistoryAiTask();
        update.setId(task.getId());
        update.setStatus(HistoryConstants.TASK_PROCESSING);
        update.setStartedAt(LocalDateTime.now());
        update.setErrorMessage(null);
        update.setUpdateTime(LocalDateTime.now());
        historyAiTaskMapper.updateById(update);
    }

    private void markFailed(HistoryAiTask task, String message)
    {
        HistoryAiTask update = new HistoryAiTask();
        update.setId(task.getId());
        update.setStatus(HistoryConstants.TASK_FAILED);
        update.setErrorMessage(message);
        update.setFinishedAt(LocalDateTime.now());
        update.setUpdateTime(LocalDateTime.now());
        historyAiTaskMapper.updateById(update);
    }

    private void enqueueAfterCommit(Long taskId)
    {
        Runnable enqueue = () -> historyExtractWorker.getObject().enqueue(taskId);
        if (TransactionSynchronizationManager.isSynchronizationActive())
        {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
            {
                @Override
                public void afterCommit()
                {
                    enqueue.run();
                }
            });
        }
        else
        {
            enqueue.run();
        }
    }

    private static String abbreviate(String text, int max)
    {
        if (text == null)
        {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static String trimToNull(String value)
    {
        if (!StringUtils.hasText(value))
        {
            return null;
        }
        return value.trim();
    }

    private static final class PersistStats
    {
        private int eventCount;
        private int personCount;
        private int placeCount;
        private int relationCount;
        private int claimCount;
    }
}
