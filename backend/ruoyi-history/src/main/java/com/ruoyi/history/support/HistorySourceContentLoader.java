package com.ruoyi.history.support;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.history.constant.HistoryConstants;
import com.ruoyi.history.domain.HistorySourceDocument;

/**
 * 从 contentText 或本地/HTTP 文件加载资料原文。
 */
@Component
public class HistorySourceContentLoader
{
    public String loadText(HistorySourceDocument document) throws IOException
    {
        if (StringUtils.hasText(document.getContentText()))
        {
            return document.getContentText();
        }
        byte[] bytes = loadBytes(document);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public byte[] loadBytes(HistorySourceDocument document) throws IOException
    {
        if (!StringUtils.hasText(document.getFileUrl()))
        {
            throw new ServiceException("资料缺少正文与文件地址，无法解析", HttpStatus.BAD_REQUEST);
        }
        String fileUrl = document.getFileUrl().trim();
        Path local = resolveLocalPath(fileUrl);
        if (local != null)
        {
            if (!Files.isRegularFile(local))
            {
                throw new ServiceException("资料文件不存在：" + local, HttpStatus.BAD_REQUEST);
            }
            return Files.readAllBytes(local);
        }
        if (fileUrl.startsWith("http://") || fileUrl.startsWith("https://"))
        {
            try (var in = URI.create(fileUrl).toURL().openStream())
            {
                return in.readAllBytes();
            }
        }
        throw new ServiceException("无法识别的文件地址：" + fileUrl, HttpStatus.BAD_REQUEST);
    }

    /**
     * 将 /profile/... 或完整 URL 中的 /profile/... 映射到本地磁盘。
     */
    Path resolveLocalPath(String fileUrl)
    {
        String pathPart = fileUrl;
        int profileIdx = fileUrl.indexOf(Constants.RESOURCE_PREFIX);
        if (profileIdx >= 0)
        {
            pathPart = fileUrl.substring(profileIdx);
        }
        if (pathPart.startsWith(Constants.RESOURCE_PREFIX))
        {
            String relative = pathPart.substring(Constants.RESOURCE_PREFIX.length());
            return Paths.get(RuoYiConfig.getProfile(), relative);
        }
        Path asPath = Paths.get(fileUrl);
        if (asPath.isAbsolute())
        {
            return asPath;
        }
        return null;
    }

    public boolean isPdf(HistorySourceDocument document)
    {
        return HistoryConstants.FILE_TYPE_PDF.equalsIgnoreCase(document.getFileType());
    }
}
