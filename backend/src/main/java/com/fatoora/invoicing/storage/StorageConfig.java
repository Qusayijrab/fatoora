package com.fatoora.invoicing.storage;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "s3", matchIfMissing = true)
    DocumentStorage s3Storage(StorageProperties properties) {
        StorageProperties.S3 cfg = properties.s3();
        S3Client client = S3Client.builder()
                .endpointOverride(URI.create(cfg.endpoint()))
                .region(Region.of(cfg.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(cfg.accessKey(), cfg.secretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(cfg.pathStyle()).build())
                .build();
        log.info("Document storage: S3 bucket '{}' at {}", cfg.bucket(), cfg.endpoint());
        return new S3DocumentStorage(client, cfg.bucket());
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "memory")
    DocumentStorage memoryStorage() {
        return new InMemoryDocumentStorage();
    }

    static final class S3DocumentStorage implements DocumentStorage {
        private final S3Client s3;
        private final String bucket;

        S3DocumentStorage(S3Client s3, String bucket) {
            this.s3 = s3;
            this.bucket = bucket;
        }

        @Override
        public Optional<byte[]> get(String key) {
            try {
                return Optional.of(s3.getObjectAsBytes(b -> b.bucket(bucket).key(key)).asByteArray());
            } catch (NoSuchKeyException e) {
                return Optional.empty();
            }
        }

        @Override
        public void put(String key, byte[] content, String contentType) {
            s3.putObject(b -> b.bucket(bucket).key(key).contentType(contentType), RequestBody.fromBytes(content));
        }
    }

    /** For tests and for running without object storage. Not shared across instances. */
    public static final class InMemoryDocumentStorage implements DocumentStorage {
        private final Map<String, byte[]> files = new ConcurrentHashMap<>();

        @Override
        public Optional<byte[]> get(String key) {
            return Optional.ofNullable(files.get(key));
        }

        @Override
        public void put(String key, byte[] content, String contentType) {
            files.put(key, content.clone());
        }

        public int size() {
            return files.size();
        }
    }
}
