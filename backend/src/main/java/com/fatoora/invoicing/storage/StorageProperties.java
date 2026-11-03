package com.fatoora.invoicing.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.storage.*}. type = s3 (default) or memory. The S3 settings work with AWS S3,
 * RustFS, MinIO, Cloudflare R2 or any S3-compatible service.
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String type, S3 s3) {

    public record S3(String endpoint, String region, String bucket, String accessKey, String secretKey, boolean pathStyle) {}
}
