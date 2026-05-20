package com.example.makemovie.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Downloads AI-generated images from temporary OSS URLs
 * and stores them persistently in MinIO (or local file system).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageStorageService {

    private final MinioClient minioClient;
    private final OssService ossService;

    @Value("${minio.bucket-name:assets}")
    private String bucketName;

    @Value("${storage.upload-dir:./uploads}")
    private String uploadDir;

    /**
     * Download an image from a URL and store it in MinIO.
     * Falls back to local file system if MinIO is unavailable.
     *
     * @param imageUrl  Temporary URL (e.g. DashScope OSS signed URL)
     * @param category  Storage category (e.g. "characters", "storyboards", "backgrounds")
     * @param name      Descriptive name for the file
     * @return Persistent URL (minio:// or file://)
     */
    public String downloadAndStore(String imageUrl, String category, String name) {
        if (imageUrl == null || imageUrl.isBlank()) return null;

        try {
            log.info("Downloading image: category={}, name={}", category, name);

            // Download bytes
            byte[] imageBytes;
            try (InputStream is = URI.create(imageUrl).toURL().openStream()) {
                imageBytes = is.readAllBytes();
            }
            log.info("Downloaded {} bytes", imageBytes.length);

            String extension = detectExtension(imageUrl);
            String objectName = String.format("%s/%s/%s.%s",
                    category, UUID.randomUUID().toString().substring(0, 8), sanitize(name), extension);

            // Try MinIO first
            String localUrl;
            try {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(new ByteArrayInputStream(imageBytes), imageBytes.length, -1)
                        .contentType("image/" + extension)
                        .build());
                localUrl = "minio://" + bucketName + "/" + objectName;
                log.info("Stored in MinIO: {}", localUrl);
            } catch (Exception e) {
                // Fallback to local file system
                Path localPath = Path.of(uploadDir, objectName);
                Files.createDirectories(localPath.getParent());
                Files.write(localPath, imageBytes);
                localUrl = "file://" + localPath.toAbsolutePath();
                log.info("Stored locally: {}", localUrl);
            }

            // Also upload to OSS (public-read) if configured
            if (ossService.isConfigured()) {
                try {
                    String ossUrl = ossService.upload(objectName, imageBytes, "image/" + extension);
                    if (ossUrl != null) {
                        log.info("Stored in OSS: {}", ossUrl);
                        return ossUrl;
                    }
                } catch (Exception e) {
                    log.warn("OSS upload failed for {}: {}", name, e.getMessage());
                }
            }
            return localUrl;
        } catch (Exception e) {
            log.error("Failed to download/store image: {} — {}", name, e.getMessage());
            return imageUrl; // Return original URL as fallback
        }
    }

    private String detectExtension(String url) {
        String lower = url.toLowerCase();
        if (lower.contains(".png")) return "png";
        if (lower.contains(".jpg") || lower.contains(".jpeg")) return "jpg";
        if (lower.contains(".webp")) return "webp";
        return "png";
    }

    private String sanitize(String name) {
        String s = name.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5_-]", "_");
        return s.length() > 30 ? s.substring(0, 30) : s;
    }

    /**
     * Store a file at a project-organized path.
     * Path format: projects/{projectId}/{subPath}
     *
     * @param projectId   Project UUID
     * @param subPath     Relative path within the project (e.g. "02-characters/苏晴/portrait.png")
     * @param data        File bytes
     * @param contentType MIME type
     * @return Persistent URL (minio:// or file://)
     */
    public String storeProjectFile(UUID projectId, String subPath, byte[] data, String contentType) {
        String objectName = String.format("projects/%s/%s", projectId, subPath);

        String localUrl;
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType(contentType)
                    .build());
            localUrl = "minio://" + bucketName + "/" + objectName;
            log.info("Stored project file: {}", localUrl);
        } catch (Exception e) {
            Path localPath = Path.of(uploadDir, objectName);
            try {
                Files.createDirectories(localPath.getParent());
                Files.write(localPath, data);
                localUrl = "file://" + localPath.toAbsolutePath();
                log.info("Stored project file locally: {}", localUrl);
            } catch (Exception ex) {
                log.error("Failed to store project file locally: {}", ex.getMessage());
                return null;
            }
        }

        // Upload image content types to OSS with public-read
        if (contentType != null && contentType.startsWith("image/")
                && ossService.isConfigured()) {
            try {
                String ossUrl = ossService.upload(objectName, data, contentType);
                if (ossUrl != null) {
                    log.info("Also uploaded to OSS: {}", ossUrl);
                    return ossUrl;
                }
            } catch (Exception e) {
                log.warn("OSS upload failed for project file (using fallback): {}", e.getMessage());
            }
        }
        return localUrl;
    }

    /**
     * Store a text file at a project-organized path.
     *
     * @param projectId Project UUID
     * @param subPath   Relative path within the project
     * @param content   Text content
     * @return Persistent URL
     */
    public String storeProjectText(UUID projectId, String subPath, String content) {
        return storeProjectFile(projectId, subPath,
                content.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "text/plain; charset=utf-8");
    }

    /**
     * Build the project file base path.
     */
    public static String projectBasePath(UUID projectId) {
        return "projects/" + projectId + "/";
    }
}
