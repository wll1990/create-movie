package com.example.makemovie.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

/**
 * Alibaba Cloud OSS service for public-read image storage.
 * Used by Wan2.7 image-to-video which requires public internet URLs.
 */
@Slf4j
@Service
public class OssService {

    private OSS ossClient;

    private final String endpoint;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final String bucketName;

    public OssService(@Value("${oss.endpoint:}") String endpoint,
                      @Value("${oss.access-key-id:}") String accessKeyId,
                      @Value("${oss.access-key-secret:}") String accessKeySecret,
                      @Value("${oss.bucket-name:make-movie-assets}") String bucketName) {
        this.endpoint = endpoint;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.bucketName = bucketName;
    }

    @PostConstruct
    void init() {
        if (endpoint == null || endpoint.isBlank() || accessKeyId == null || accessKeyId.isBlank()) {
            log.info("OSS: not configured (endpoint/key blank), public image storage disabled");
            return;
        }
        this.ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        log.info("OSS: initialized endpoint={}, bucket={}", endpoint, bucketName);
    }

    public boolean isConfigured() {
        return ossClient != null;
    }

    /**
     * Upload data to OSS with public-read ACL.
     *
     * @param objectKey   full object path, e.g. "projects/{uuid}/02-characters/name/portrait.png"
     * @param data        file bytes
     * @param contentType MIME type
     * @return public HTTPS URL, or null on failure
     */
    public String upload(String objectKey, byte[] data, String contentType) {
        if (!isConfigured()) {
            log.warn("OSS: upload skipped (not configured) for {}", objectKey);
            return null;
        }
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            metadata.setContentLength(data.length);

            PutObjectRequest request = new PutObjectRequest(
                    bucketName, objectKey,
                    new ByteArrayInputStream(data), metadata);

            ossClient.putObject(request);
            String publicUrl = buildPublicUrl(objectKey);
            log.info("OSS: uploaded {} ({} bytes) -> {}", objectKey, data.length, publicUrl);
            return publicUrl;
        } catch (Exception e) {
            log.error("OSS: upload failed for {} — {}", objectKey, e.getMessage());
            return null;
        }
    }

    public String buildPublicUrl(String objectKey) {
        return String.format("https://%s.%s/%s", bucketName, endpoint, objectKey);
    }

    @PreDestroy
    void shutdown() {
        if (ossClient != null) {
            ossClient.shutdown();
            log.info("OSS: client shutdown");
        }
    }
}
