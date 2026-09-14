package com.streamplatform.streamapi.service;

import java.io.InputStream;

import io.minio.StatObjectResponse;

public interface MinioStorageService {

    void ensureBucketExists();

    void uploadFile(String objectKey, InputStream inputStream, long size, String contentType);

    InputStream getObjectStream(String objectKey);

    InputStream getObjectStream(String objectKey, long offset, long length);

    StatObjectResponse getObjectMetadata(String objectKey);

    void deleteObject(String objectKey);

    boolean doesObjectExist(String objectKey);
}
