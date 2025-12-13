package com.example.genai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Service
public class S3StorageService {

    private final S3Client s3;
    private final String bucket;

    public S3StorageService(
            @Value("${app.s3.bucket}") String bucket,
            @Value("${app.s3.region}") String region
    ) {
        this.bucket = bucket;
        this.s3 = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    public String uploadBytes(String key, byte[] data, String contentType) {
        String safeKey = key.replace(" ", "_");

        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(bucket)
                .key(safeKey)
                .contentType(contentType)
                .build();

        s3.putObject(req, RequestBody.fromBytes(data));

        return safeKey;
    }

    public String getPublicUrl(String key) {
        String encoded = URLEncoder.encode(key, StandardCharsets.UTF_8);
        return "https://" + bucket + ".s3.amazonaws.com/" + encoded;
    }
}

