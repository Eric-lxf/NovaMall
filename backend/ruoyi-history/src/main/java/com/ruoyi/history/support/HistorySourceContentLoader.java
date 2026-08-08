package com.ruoyi.history.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;

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
    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int READ_TIMEOUT_MILLIS = 15_000;
    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_DOWNLOAD_BYTES = 15 * 1024 * 1024;
    private static final int MAX_TEXT_CHARS = 2_000_000;

    private static final Set<String> BLOCKED_HOST_NAMES = Set.of(
            "localhost",
            "localhost.localdomain",
            "instance-data",
            "metadata.google.internal",
            "metadata.goog");

    public String loadText(HistorySourceDocument document) throws IOException
    {
        if (StringUtils.hasText(document.getContentText()))
        {
            return validateTextSize(document.getContentText());
        }
        byte[] bytes = loadBytes(document);
        return validateTextSize(new String(bytes, StandardCharsets.UTF_8));
    }

    public byte[] loadBytes(HistorySourceDocument document) throws IOException
    {
        if (StringUtils.hasText(document.getContentText()))
        {
            validateTextSize(document.getContentText());
        }
        if (!StringUtils.hasText(document.getFileUrl()))
        {
            throw badRequest("资料缺少正文与文件地址，无法解析");
        }

        String fileUrl = document.getFileUrl().trim();
        Path local = resolveLocalPath(fileUrl);
        if (local != null)
        {
            return readLocalFile(local);
        }

        URI remoteUri = parseRemoteUri(fileUrl);
        if (remoteUri != null)
        {
            return downloadRemote(remoteUri);
        }
        throw badRequest("无法识别文件地址，仅支持 /profile 内文件或 HTTP/HTTPS 地址");
    }

    /**
     * 将 /profile/...（包括完整 URL 中的该路径）或 profile 内绝对路径映射到本地磁盘。
     * 返回的路径只做词法约束；读取前还会通过 real path 检查符号链接逃逸。
     */
    Path resolveLocalPath(String fileUrl)
    {
        try
        {
            Path profileRoot = configuredProfileRoot();
            String resourcePath = extractProfileResourcePath(fileUrl);
            Path candidate;
            if (resourcePath != null)
            {
                String relative = resourcePath.substring(Constants.RESOURCE_PREFIX.length())
                        .replaceFirst("^[\\\\/]+", "");
                candidate = profileRoot.resolve(relative);
            }
            else
            {
                if (isHttpUri(fileUrl))
                {
                    return null;
                }
                Path asPath = Paths.get(fileUrl);
                if (!asPath.isAbsolute())
                {
                    return null;
                }
                candidate = asPath;
            }

            Path normalized = candidate.toAbsolutePath().normalize();
            if (!normalized.startsWith(profileRoot))
            {
                throw badRequest("本地资料文件必须位于系统上传目录内");
            }
            return normalized;
        }
        catch (InvalidPathException | SecurityException ex)
        {
            throw badRequest("本地资料文件地址无效");
        }
    }

    private byte[] readLocalFile(Path candidate)
    {
        try
        {
            Path profileRealPath = configuredProfileRoot().toRealPath();
            Path fileRealPath = candidate.toRealPath();
            if (!fileRealPath.startsWith(profileRealPath) || !Files.isRegularFile(fileRealPath))
            {
                throw badRequest("资料文件不存在或不可读取");
            }
            long size = Files.size(fileRealPath);
            if (size > MAX_DOWNLOAD_BYTES)
            {
                throw contentTooLarge();
            }
            try (InputStream in = Files.newInputStream(fileRealPath))
            {
                return readLimited(in);
            }
        }
        catch (ServiceException ex)
        {
            throw ex;
        }
        catch (IOException | SecurityException ex)
        {
            // 不向任务错误信息暴露服务器本地绝对路径。
            throw badRequest("资料文件不存在或不可读取");
        }
    }

    private byte[] downloadRemote(URI initialUri)
    {
        URI current = initialUri;
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++)
        {
            validateRemoteUri(current);
            HttpURLConnection connection = null;
            try
            {
                connection = (HttpURLConnection) current.toURL().openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
                connection.setReadTimeout(READ_TIMEOUT_MILLIS);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "text/plain,text/markdown,application/pdf,application/octet-stream;q=0.8");
                connection.setRequestProperty("Accept-Encoding", "identity");
                connection.setRequestProperty("User-Agent", "NovaMall-History-Source-Loader/1.0");

                int status = connection.getResponseCode();
                if (isRedirect(status))
                {
                    if (redirects >= MAX_REDIRECTS)
                    {
                        throw badRequest("远程资料重定向次数过多");
                    }
                    String location = connection.getHeaderField("Location");
                    if (!StringUtils.hasText(location))
                    {
                        throw badRequest("远程资料返回了无效重定向");
                    }
                    current = resolveRedirect(current, location);
                    continue;
                }
                if (status < 200 || status >= 300)
                {
                    throw badRequest("远程资料下载失败（HTTP " + status + "）");
                }

                long contentLength = connection.getContentLengthLong();
                if (contentLength > MAX_DOWNLOAD_BYTES)
                {
                    throw contentTooLarge();
                }
                try (InputStream in = connection.getInputStream())
                {
                    return readLimited(in);
                }
            }
            catch (ServiceException ex)
            {
                throw ex;
            }
            catch (IOException | IllegalArgumentException ex)
            {
                throw badRequest("远程资料下载失败");
            }
            finally
            {
                if (connection != null)
                {
                    connection.disconnect();
                }
            }
        }
        throw badRequest("远程资料重定向次数过多");
    }

    private URI parseRemoteUri(String fileUrl)
    {
        try
        {
            URI uri = new URI(fileUrl);
            String scheme = uri.getScheme();
            if (scheme == null)
            {
                return null;
            }
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
            {
                return null;
            }
            return uri;
        }
        catch (URISyntaxException ex)
        {
            throw badRequest("远程资料地址无效");
        }
    }

    private boolean isHttpUri(String value)
    {
        try
        {
            String scheme = new URI(value).getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        }
        catch (URISyntaxException ex)
        {
            return false;
        }
    }

    private void validateRemoteUri(URI uri)
    {
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
        {
            throw badRequest("远程资料仅支持 HTTP/HTTPS 地址");
        }
        if (uri.getUserInfo() != null || uri.getFragment() != null || !StringUtils.hasText(uri.getHost()))
        {
            throw badRequest("远程资料地址无效");
        }

        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.endsWith("."))
        {
            host = host.substring(0, host.length() - 1);
        }
        if (isBlockedHostName(host))
        {
            throw blockedRemoteAddress();
        }

        try
        {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0)
            {
                throw badRequest("远程资料域名无法解析");
            }
            for (InetAddress address : addresses)
            {
                if (isBlockedAddress(address))
                {
                    throw blockedRemoteAddress();
                }
            }
        }
        catch (UnknownHostException ex)
        {
            throw badRequest("远程资料域名无法解析");
        }
    }

    private boolean isBlockedHostName(String host)
    {
        return BLOCKED_HOST_NAMES.contains(host)
                || host.endsWith(".localhost")
                || host.endsWith(".local")
                || host.endsWith(".internal")
                || host.endsWith(".lan")
                || host.endsWith(".home");
    }

    private boolean isBlockedAddress(InetAddress address)
    {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress())
        {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address && bytes.length == 4)
        {
            return isBlockedIpv4(bytes);
        }
        if (address instanceof Inet6Address && bytes.length == 16)
        {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            // fc00::/7（ULA）、2001:db8::/32（文档地址）以及 IPv4 兼容/映射地址。
            return (first & 0xfe) == 0xfc
                    || (first == 0x20 && second == 0x01 && (bytes[2] & 0xff) == 0x0d && (bytes[3] & 0xff) == 0xb8)
                    || isIpv4CompatibleOrMapped(bytes);
        }
        return true;
    }

    private boolean isBlockedIpv4(byte[] bytes)
    {
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        int third = bytes[2] & 0xff;
        return first == 0
                || first == 10
                || first == 127
                || (first == 100 && second >= 64 && second <= 127)
                || (first == 100 && second == 100 && third == 100) // 阿里云 metadata: 100.100.100.200
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 0 && third == 0)
                || (first == 192 && second == 0 && third == 2)
                || (first == 192 && second == 168)
                || (first == 198 && (second == 18 || second == 19))
                || (first == 198 && second == 51 && third == 100)
                || (first == 203 && second == 0 && third == 113)
                || first >= 224;
    }

    private boolean isIpv4CompatibleOrMapped(byte[] bytes)
    {
        for (int i = 0; i < 10; i++)
        {
            if (bytes[i] != 0)
            {
                return false;
            }
        }
        boolean mapped = (bytes[10] & 0xff) == 0xff && (bytes[11] & 0xff) == 0xff;
        boolean compatible = bytes[10] == 0 && bytes[11] == 0;
        if (!mapped && !compatible)
        {
            return false;
        }
        return isBlockedIpv4(new byte[] { bytes[12], bytes[13], bytes[14], bytes[15] });
    }

    private URI resolveRedirect(URI current, String location)
    {
        try
        {
            URI redirected = current.resolve(new URI(location));
            validateRemoteUri(redirected);
            return redirected;
        }
        catch (URISyntaxException | IllegalArgumentException ex)
        {
            throw badRequest("远程资料返回了无效重定向");
        }
    }

    private byte[] readLimited(InputStream in) throws IOException
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream(8192);
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = in.read(buffer)) != -1)
        {
            total += read;
            if (total > MAX_DOWNLOAD_BYTES)
            {
                throw contentTooLarge();
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private String validateTextSize(String text)
    {
        if (text.length() > MAX_TEXT_CHARS)
        {
            throw badRequest("资料正文过大，最多允许 " + MAX_TEXT_CHARS + " 个字符");
        }
        return text;
    }

    private Path configuredProfileRoot()
    {
        if (!StringUtils.hasText(RuoYiConfig.getProfile()))
        {
            throw badRequest("系统上传目录未配置");
        }
        return Paths.get(RuoYiConfig.getProfile()).toAbsolutePath().normalize();
    }

    private String extractProfileResourcePath(String fileUrl)
    {
        if (isProfileResourcePath(fileUrl))
        {
            return fileUrl;
        }
        try
        {
            URI uri = new URI(fileUrl);
            String path = uri.getPath();
            return isProfileResourcePath(path) ? path : null;
        }
        catch (URISyntaxException ex)
        {
            return null;
        }
    }

    private boolean isProfileResourcePath(String path)
    {
        return path != null
                && (path.equals(Constants.RESOURCE_PREFIX)
                        || path.startsWith(Constants.RESOURCE_PREFIX + "/")
                        || path.startsWith(Constants.RESOURCE_PREFIX + "\\"));
    }

    private boolean isRedirect(int status)
    {
        return status == HttpURLConnection.HTTP_MOVED_PERM
                || status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_SEE_OTHER
                || status == 307
                || status == 308;
    }

    private ServiceException contentTooLarge()
    {
        return badRequest("资料文件过大，最多允许 " + (MAX_DOWNLOAD_BYTES / 1024 / 1024) + "MB");
    }

    private ServiceException blockedRemoteAddress()
    {
        return badRequest("远程资料地址不允许访问本机、私网、链路本地或云元数据服务");
    }

    private ServiceException badRequest(String message)
    {
        return new ServiceException(message, HttpStatus.BAD_REQUEST);
    }

    public boolean isPdf(HistorySourceDocument document)
    {
        return HistoryConstants.FILE_TYPE_PDF.equalsIgnoreCase(document.getFileType());
    }
}
