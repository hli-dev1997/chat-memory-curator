package com.chatgpt.memory.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * 智能多模态图片与文件资产提供服务控制器
 * <p>
 * 支持将 ChatGPT HTML 导出的资产指针（如 file_xxx, sediment://file_xxx, file-service://file-xxx）
 * 动态前缀匹配到磁盘上的真实物理文件（如 file_xxx-sanitized.jpg, file-xxx.png 等），解决扩展名丢失和 404 问题。
 * </p>
 */
@Slf4j
@RestController
public class ChatAssetController {

    private static final String EXPORT_DIR_PATH = "e:/data/chatGPT_back/chatGPT导出20251214";
    private final File exportDir = new File(EXPORT_DIR_PATH);

    @GetMapping("/chat-assets/**")
    public ResponseEntity<Resource> serveAsset(HttpServletRequest request) {
        String fullPath = request.getRequestURI(); // 例如 /chat-assets/sediment://file_00000000654872099dafc576c2d84731
        String assetPath = fullPath.substring("/chat-assets/".length());

        // 1. 清理可能携带的协议头 (如 sediment://, sediment:/, file-service://, file-service:/ 等)
        if (assetPath.contains(":")) {
            assetPath = assetPath.substring(assetPath.lastIndexOf(":") + 1);
        }
        while (assetPath.startsWith("/")) {
            assetPath = assetPath.substring(1);
        }

        if (assetPath.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        // 2. 优先检索磁盘精确同名文件
        File file = new File(exportDir, assetPath);
        if (file.exists() && file.isFile()) {
            return buildFileResponse(file);
        }

        // 2. 深度递归检索导出目录下的所有子目录（包含对话 ID 子目录、audio 目录等）
        final String searchPrefix = assetPath;
        try (var stream = Files.walk(exportDir.toPath())) {
            var match = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String fileName = p.getFileName().toString();
                        return fileName.equalsIgnoreCase(searchPrefix) || fileName.startsWith(searchPrefix);
                    })
                    .findFirst();

            if (match.isPresent()) {
                File matchedFile = match.get().toFile();
                log.info("Asset recursive match succeeded: [{}] -> [{}]", assetPath, matchedFile.getAbsolutePath());
                return buildFileResponse(matchedFile);
            }
        } catch (IOException e) {
            log.error("Error walking export directory for assetPath: {}", assetPath, e);
        }

        log.warn("Asset file not found on disk, returning placeholder: assetPath={}", assetPath);
        return buildPlaceholderResponse(assetPath);
    }

    private ResponseEntity<Resource> buildPlaceholderResponse(String assetId) {
        String shortId = assetId.length() > 12 ? assetId.substring(0, 12) + "..." : assetId;
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"320\" height=\"120\" viewBox=\"0 0 320 120\">"
                + "<rect width=\"320\" height=\"120\" fill=\"#1c2128\" rx=\"6\"/>"
                + "<text x=\"160\" y=\"44\" text-anchor=\"middle\" font-family=\"monospace\" font-size=\"28\" fill=\"#484f58\">🖼️</text>"
                + "<text x=\"160\" y=\"70\" text-anchor=\"middle\" font-family=\"monospace\" font-size=\"11\" fill=\"#6e7681\">图片在导出包中不存在</text>"
                + "<text x=\"160\" y=\"88\" text-anchor=\"middle\" font-family=\"monospace\" font-size=\"9\" fill=\"#484f58\">" + shortId + "</text>"
                + "</svg>";
        byte[] bytes = svg.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        org.springframework.core.io.ByteArrayResource svgResource = new org.springframework.core.io.ByteArrayResource(bytes);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/svg+xml"))
                .body(svgResource);
    }

    private ResponseEntity<Resource> buildFileResponse(File file) {
        FileSystemResource resource = new FileSystemResource(file);
        String contentType = null;
        try {
            contentType = Files.probeContentType(file.toPath());
        } catch (IOException e) {
            log.warn("Failed to probe content type for file: {}", file.getAbsolutePath());
        }

        if (contentType == null) {
            String nameLower = file.getName().toLowerCase();
            if (nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg")) {
                contentType = "image/jpeg";
            } else if (nameLower.endsWith(".png")) {
                contentType = "image/png";
            } else if (nameLower.endsWith(".webp")) {
                contentType = "image/webp";
            } else if (nameLower.endsWith(".gif")) {
                contentType = "image/gif";
            } else if (nameLower.endsWith(".pdf")) {
                contentType = "application/pdf";
            } else {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }
}
