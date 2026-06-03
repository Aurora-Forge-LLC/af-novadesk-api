package com.af.novadesk.api.asset.service.impl;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.af.novadesk.api.asset.entity.Asset;
import com.af.novadesk.api.asset.exception.AssetNotFoundException;
import com.af.novadesk.api.asset.repository.AssetRepository;
import com.af.novadesk.api.asset.service.AssetService;
import com.af.novadesk.api.common.service.FileStorageService;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.UUID;

/**
 * Generates QR code labels for assets (LLR-AST-01.4).
 *
 * <p>QR content JSON: {@code {"assetId":"...","serial":"...","type":"...","entity":"..."}}
 * The generated PNG is uploaded to MinIO and the URL stored on the asset record.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QrCodeServiceImpl {

    private static final int    QR_SIZE    = 300;
    private static final String MIME_TYPE  = "image/png";
    private static final String QR_FOLDER  = "assets/qr-codes";

    private final AssetRepository        assetRepository;
    private final FileStorageService     fileStorageService;
    private final FinanceSecurityContext securityContext;

    /**
     * Generates a QR code PNG for the asset, uploads to MinIO, and persists the URL.
     *
     * @return the public MinIO URL of the generated QR code image
     */
    public String generateAndStore(UUID assetId) {
        UUID orgId = securityContext.getOrganizationId();
        Asset asset = assetRepository.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> new AssetNotFoundException(assetId));

        String content = buildQrContent(asset);
        byte[] png     = generatePng(content);

        String key = QR_FOLDER + "/" + assetId + ".png";
        fileStorageService.upload(key, png, MIME_TYPE);

        // Build a presigned URL via FileStorageService (15-min expiry is fine for QR labels)
        String url = fileStorageService.generatePresignedUrl(key, java.time.Duration.ofDays(7));

        asset.setQrCodeUrl(url);
        assetRepository.save(asset);
        log.info("QR code generated for asset {} → {}", assetId, key);
        return url;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildQrContent(Asset asset) {
        return String.format(
            "{\"assetId\":\"%s\",\"serial\":\"%s\",\"type\":\"%s\",\"entity\":\"%s\"}",
            asset.getId(),
            asset.getSerialNumber(),
            asset.getAssetType(),
            asset.getLegalEntity().getEntityCode()
        );
    }

    private byte[] generatePng(String content) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = Map.of(
                EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN, 2
            );
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | java.io.IOException e) {
            throw new RuntimeException("Failed to generate QR code for content: " + content, e);
        }
    }
}
