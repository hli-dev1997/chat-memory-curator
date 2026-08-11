package com.chatgpt.memory.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 消息内嵌本地图片解析与 Base64 DataURI 转换工具类
 * <p>
 * 提取文本中关联的图片引用，读取本地导出的图片文件并转码为符合 OpenAI / LangChain4j 多模态标准的
 * {@code data:image/<mime>;base64,<data>} DataURI 格式。
 * </p>
 *
 * @author Antigravity
 */
@Slf4j
public final class ImageBase64Util {

    private static final String EXPORT_DIR_PATH = "e:/data/chatGPT_back/chatGPT导出20251214";

    /**
     * 匹配 Markdown 图片语法或 URL 路径，例如：
     * ![图片](/chat-assets/file_00000000d2e87206b7b473d12a9e9bdf)
     * 或 /chat-assets/sediment://file_xxx
     */
    private static final Pattern IMG_PATTERN = Pattern.compile(
            "(?i)!\\[[^\\]]*\\]\\(/chat-assets/(?:[^)]+://)?([^)]+)\\)"
    );

    private ImageBase64Util() {
        // 工具类私有构造方法
    }

    /**
     * 从多段文本中提取所有关联的本地图片，并读取编码为 Base64 DataURI 列表
     *
     * @param texts 消息正文参数（可传多个文本）
     * @return Base64 DataURI 字符串列表
     */
    public static List<String> extractBase64Images(final String... texts) {
        final List<String> dataUris = new ArrayList<>();
        if (texts == null || texts.length == 0) {
            return dataUris;
        }

        final File exportDir = new File(EXPORT_DIR_PATH);
        if (!exportDir.exists() || !exportDir.isDirectory()) {
            log.warn("[ImageBase64Util] 导出数据源根目录不存在: {}", EXPORT_DIR_PATH);
            return dataUris;
        }

        for (final String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }

            final Matcher matcher = IMG_PATTERN.matcher(text);
            while (matcher.find()) {
                final String fileName = matcher.group(1).trim();
                final String dataUri = resolveAndEncodeFile(exportDir, fileName);
                if (dataUri != null && !dataUri.isBlank()) {
                    dataUris.add(dataUri);
                }
            }
        }

        return dataUris;
    }

    /**
     * 在导出目录中查找文件并转码为 Base64 DataURI
     *
     * @param exportDir 本地导出根目录
     * @param fileName 目标文件名
     * @return Base64 DataURI，若未找到或读取失败则返回 null
     */
    private static String resolveAndEncodeFile(final File exportDir, final String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.contains("..")) {
            log.warn("[ImageBase64Util] 检测到非法图片文件名参数: {}", fileName);
            return null;
        }

        final String cleanName = new File(fileName.trim()).getName();
        File file = new File(exportDir, cleanName);
        if (!file.exists()) {
            // 匹配动态前缀衍生文件（例如 file_xxx -> file_xxx-sanitized.png）
            final File[] prefixMatches = exportDir.listFiles((dir, name) -> name.startsWith(cleanName));
            if (prefixMatches != null && prefixMatches.length > 0) {
                file = prefixMatches[0];
            } else {
                // 支持在子目录中深度匹配
                final File[] subDirs = exportDir.listFiles(File::isDirectory);
                if (subDirs != null) {
                    for (final File subDir : subDirs) {
                        final File candidate = new File(subDir, cleanName);
                        if (candidate.exists()) {
                            file = candidate;
                            break;
                        }
                        final File[] subMatches = subDir.listFiles((dir, name) -> name.startsWith(cleanName));
                        if (subMatches != null && subMatches.length > 0) {
                            file = subMatches[0];
                            break;
                        }
                    }
                }
            }
        }

        if (!file.exists() || !file.isFile()) {
            log.warn("[ImageBase64Util] 未在磁盘找到关联图片文件: {}", cleanName);
            return null;
        }

        // 10MB 单张图片上限保护，规避大文件导致内存溢出 (OOM)
        if (file.length() > 10 * 1024 * 1024L) {
            log.warn("[ImageBase64Util] 图片文件超过 10MB 字节限制，跳过转码: {}", cleanName);
            return null;
        }

        try {
            final byte[] bytes = Files.readAllBytes(file.toPath());
            final String mimeType = detectImageMimeType(bytes, file.getName());
            if (mimeType == null) {
                log.warn("[ImageBase64Util] 图片文件 Magic Bytes 校验失败 (非标准 PNG/JPEG/WEBP/GIF/BMP 格式或文件已损坏): {}", file.getAbsolutePath());
                return null;
            }

            final String base64 = Base64.getEncoder().encodeToString(bytes);
            log.debug("[ImageBase64Util] 成功转码本地图片为 Base64 | 文件: {}, MIME: {}, 字节数: {}", file.getName(), mimeType, bytes.length);
            return "data:" + mimeType + ";base64," + base64;
        } catch (IOException e) {
            log.error("[ImageBase64Util] 读取图片文件失败: {}", file.getAbsolutePath(), e);
            return null;
        }
    }

    /**
     * 校验 byte 数组头标志 (Magic Bytes) 并获取标准 MIME 类型。
     * 支持 PNG, JPEG, GIF, WEBP, BMP。若校验失败则返回 null。
     *
     * @param bytes 文件字节数组
     * @param fileName 文件名（用于辅助判断后缀名）
     * @return MIME 字符串，若非合法图片格式则返回 null
     */
    private static String detectImageMimeType(final byte[] bytes, final String fileName) {
        if (bytes == null || bytes.length < 4) {
            return null;
        }

        // PNG: 89 50 4E 47
        if ((bytes[0] & 0xFF) == 0x89 && (bytes[1] & 0xFF) == 0x50 &&
            (bytes[2] & 0xFF) == 0x4E && (bytes[3] & 0xFF) == 0x47) {
            return "image/png";
        }

        // JPEG: FF D8 FF
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }

        // GIF: 47 49 46 38 ("GIF8")
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8') {
            return "image/gif";
        }

        // WEBP: 52 49 46 46 ... 57 45 42 50 ("RIFF" ... "WEBP")
        if (bytes.length >= 12 &&
            bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' &&
            bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }

        // BMP: 42 4D ("BM")
        if (bytes[0] == 'B' && bytes[1] == 'M') {
            return "image/bmp";
        }

        // 若后缀明确且匹配常见格式，兜底返回
        final String lower = fileName != null ? fileName.toLowerCase() : "";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".bmp")) return "image/bmp";

        return null;
    }
}
