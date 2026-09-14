package com.streamplatform.streamapi.service.impl;

import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.streamplatform.streamapi.exception.ApiException;
import com.streamplatform.streamapi.service.MinioStorageService;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import jakarta.annotation.PostConstruct;

@Service
public class MinioStorageServiceImpl implements MinioStorageService {

    private static final Logger logger = LoggerFactory.getLogger(MinioStorageServiceImpl.class);

    private final MinioClient minioClient;

    @Value("${minio.bucket-name:stratalive-videos}")
    private String bucketName;

    public MinioStorageServiceImpl(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @PostConstruct
    public void init() {
        try {
            ensureBucketExists();
        } catch (Exception e) {
            logger.warn("Could not verify MinIO bucket on startup (MinIO may still be starting up): {}", e.getMessage());
        }
    }

    @Override
    public void ensureBucketExists() {
        try {
            boolean found = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build());
                logger.info("Created MinIO bucket: {}", bucketName);
            }
        } catch (Exception e) {
            logger.error("Error ensuring MinIO bucket exists: {}", e.getMessage());
            throw new ApiException("Storage service error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void uploadFile(String objectKey, InputStream inputStream, long size, String contentType) {
        try {
            ensureBucketExists();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .stream(inputStream, size, -1)
                            .contentType(contentType != null ? contentType : "video/mp4")
                            .build());
            logger.info("Uploaded object to MinIO: bucket={}, objectKey={}, size={}", bucketName, objectKey, size);
        } catch (Exception e) {
            logger.error("Failed to upload object to MinIO: {}", e.getMessage());
            throw new ApiException("Failed to store video in object storage: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public InputStream getObjectStream(String objectKey) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build());
        } catch (Exception e) {
            logger.error("Failed to retrieve object stream from MinIO: {}", e.getMessage());
            throw new ApiException("Video file not found or inaccessible.", HttpStatus.NOT_FOUND);
        }
    }

    @Override
    public InputStream getObjectStream(String objectKey, long offset, long length) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .offset(offset)
                            .length(length)
                            .build());
        } catch (Exception e) {
            logger.error("Failed to retrieve ranged object stream from MinIO: {}", e.getMessage());
            throw new ApiException("Video file range not found or inaccessible.", HttpStatus.NOT_FOUND);
        }
    }

    @Override
    public StatObjectResponse getObjectMetadata(String objectKey) {
        try {
            return minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build());
        } catch (Exception e) {
            logger.error("Failed to stat object in MinIO: {}", e.getMessage());
            throw new ApiException("Video file metadata not found.", HttpStatus.NOT_FOUND);
        }
    }

    @Override
    public void deleteObject(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build());
            logger.info("Deleted object from MinIO: bucket={}, objectKey={}", bucketName, objectKey);
        } catch (Exception e) {
            logger.warn("Failed to delete object from MinIO: {}", e.getMessage());
        }
    }

    @Override
    public boolean doesObjectExist(String objectKey) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
