package com.sonora.file.service;

import com.sonora.common.constant.Constants;
import io.minio.*;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 文件服务
 */
@Service
public class MinioService {

    private static final Logger log = LoggerFactory.getLogger(MinioService.class);
    private static final String PREVIEW_PATH = "/api/files/preview";

    private final MinioClient minioClient;
    private final String endpoint;

    @Value("${minio.bucket}")
    private String bucket;

    public MinioService(@Value("${minio.endpoint}") String endpoint,
                        @Value("${minio.access-key}") String accessKey,
                        @Value("${minio.secret-key}") String secretKey) {
        this.endpoint = endpoint;
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    /** 上传文件 */
    public String upload(MultipartFile file, String dir) throws Exception {
        ensureBucket();
        String objectKey = dir + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(file.getContentType())
                .build());
        log.info("文件上传成功: {}", objectKey);
        return objectKey;
    }

    /** 获取文件访问 URL (预签名，默认 1 小时有效) */
    public String getPresignedUrl(String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .method(Method.GET)
                    .expiry(1, TimeUnit.HOURS)
                    .build());
        } catch (Exception e) {
            log.error("生成预签名 URL 失败: {}", objectKey, e);
            return null;
        }
    }

    /** 生成稳定预览地址 */
    public String buildPreviewUrl(String objectKey) {
        String normalized = normalizeForStorage(objectKey);
        if (!StringUtils.hasText(normalized) || isDefaultAsset(normalized)) {
            return normalized;
        }
        return PREVIEW_PATH + "?key=" + UriUtils.encodeQueryParam(normalized, StandardCharsets.UTF_8);
    }

    /** 将历史签名 URL / 预览 URL / objectKey 统一转换为可入库值 */
    public String normalizeForStorage(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        String value = rawValue.trim();
        if (isDefaultAsset(value)) {
            return value;
        }
        String objectKey = extractObjectKey(value);
        return StringUtils.hasText(objectKey) ? objectKey : value;
    }

    /** 将数据库中的值转换为稳定的预览地址 */
    public String resolvePreviewUrl(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        String value = rawValue.trim();
        if (isDefaultAsset(value)) {
            return value;
        }
        String objectKey = extractObjectKey(value);
        return StringUtils.hasText(objectKey) ? buildPreviewUrl(objectKey) : value;
    }

    /** 提取 objectKey，兼容旧签名 URL / 稳定预览 URL / 直接 objectKey */
    public String extractObjectKey(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        String value = rawValue.trim();
        if (isDefaultAsset(value)) {
            return value;
        }
        if (value.startsWith(PREVIEW_PATH)) {
            String key = UriComponentsBuilder.fromUriString(value).build().getQueryParams().getFirst("key");
            return StringUtils.hasText(key) ? key.trim() : null;
        }
        if (!value.contains("://")) {
            if (value.startsWith("/")) {
                return null;
            }
            return value;
        }
        try {
            URI uri = URI.create(value);
            if (PREVIEW_PATH.equals(uri.getPath())) {
                String key = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("key");
                return StringUtils.hasText(key) ? key.trim() : null;
            }
            String path = uri.getPath();
            String bucketPrefix = "/" + bucket + "/";
            if (StringUtils.hasText(path) && path.startsWith(bucketPrefix)) {
                return UriUtils.decode(path.substring(bucketPrefix.length()), StandardCharsets.UTF_8);
            }
            String endpointHost = safeHost(endpoint);
            if (StringUtils.hasText(endpointHost) && endpointHost.equalsIgnoreCase(uri.getHost())
                    && StringUtils.hasText(path) && path.startsWith("/")) {
                String normalizedPath = path.substring(1);
                String prefixedBucket = bucket + "/";
                if (normalizedPath.startsWith(prefixedBucket)) {
                    return UriUtils.decode(normalizedPath.substring(prefixedBucket.length()), StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            log.debug("提取 objectKey 失败: {}", rawValue, e);
        }
        return null;
    }

    public String getContentType(String objectKey) {
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
            return stat.contentType();
        } catch (Exception e) {
            log.warn("读取文件类型失败: {}", objectKey, e);
            return null;
        }
    }

    /** 获取文件输入流 */
    public InputStream getObject(String objectKey) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build());
    }

    /** 按字节范围获取文件输入流，避免 Range 请求从对象开头读取并丢弃数据。 */
    public InputStream getObject(String objectKey, long offset, long length) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .offset(offset)
                .length(length)
                .build());
    }

    /** 获取对象的实际字节大小。 */
    public long getObjectSize(String objectKey) throws Exception {
        return minioClient.statObject(StatObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build())
                .size();
    }

    /** 删除文件 */
    public void delete(String objectKey) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build());
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("创建 MinIO Bucket: {}", bucket);
        }
    }

    private boolean isDefaultAsset(String value) {
        return Constants.DEFAULT_AVATAR.equals(value) || Constants.DEFAULT_COVER.equals(value);
    }

    private String safeHost(String uriValue) {
        try {
            return URI.create(uriValue).getHost();
        } catch (Exception ignored) {
            return null;
        }
    }
}
