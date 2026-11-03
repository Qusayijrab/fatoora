package com.fatoora.invoicing.storage;

import java.util.Optional;

/**
 * Where generated files (PDFs) live. S3-compatible in every real environment; an
 * in-memory version for tests. Keys always start with the organization id, so one
 * tenant's files can never be addressed through another tenant's key.
 */
public interface DocumentStorage {

    Optional<byte[]> get(String key);

    void put(String key, byte[] content, String contentType);
}
