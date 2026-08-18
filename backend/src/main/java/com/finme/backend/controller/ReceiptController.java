package com.finme.backend.controller;

import com.finme.backend.security.AuthenticatedUser;
import com.finme.backend.dto.ReceiptResponse;
import com.finme.backend.entity.Receipt;
import com.finme.backend.exception.InvalidReceiptFileException;
import com.finme.backend.service.ReceiptIngestionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@RestController
@RequestMapping("/api/receipts")
public class ReceiptController {

    // HEIC (the iPhone default) isn't confirmed supported by any of the three vision APIs -
    // reject cleanly rather than fail confusingly downstream.
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE);

    private final ReceiptIngestionService receiptIngestionService;
    private final AuthenticatedUser authenticatedUser;

    public ReceiptController(ReceiptIngestionService receiptIngestionService, AuthenticatedUser authenticatedUser) {
        this.receiptIngestionService = receiptIngestionService;
        this.authenticatedUser = authenticatedUser;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReceiptResponse> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidReceiptFileException("Uploaded file is empty");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new InvalidReceiptFileException("Only JPEG or PNG images are supported");
        }

        Receipt receipt = receiptIngestionService.ingest(authenticatedUser.currentUserId(), file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ReceiptResponse.from(receipt));
    }
}
