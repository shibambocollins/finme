package com.finme.backend.service;

import com.finme.backend.ai.AllAiProvidersFailedException;
import com.finme.backend.ai.ExtractedTransaction;
import com.finme.backend.ai.VisionAiProvider;
import com.finme.backend.entity.PaymentMethod;
import com.finme.backend.entity.Receipt;
import com.finme.backend.entity.ReceiptStatus;
import com.finme.backend.entity.SourceType;
import com.finme.backend.entity.Transaction;
import com.finme.backend.entity.TransactionDirection;
import com.finme.backend.entity.TransactionStatus;
import com.finme.backend.exception.InvalidReceiptFileException;
import com.finme.backend.exception.ReceiptProcessingException;
import com.finme.backend.exception.UnrecognisedDocumentException;
import com.finme.backend.repository.ReceiptRepository;
import com.finme.backend.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Receipt Upload Flow (docs/03-system-design.md Sec. 4): straight from upload to a
 * vision-capable AI call - no local extraction step (there's no text layer in a photo) and no
 * redaction pass (see the plan's Context note: a deliberate, risk-accepted gap, confirmed with
 * Collins, not an oversight). Only ingestion path in the app that sends raw external input to
 * an AI provider unfiltered.
 */
@Service
public class ReceiptIngestionService {

    private final ReceiptRepository receiptRepository;
    private final TransactionRepository transactionRepository;
    private final VisionAiProvider visionAiProvider;

    public ReceiptIngestionService(
            ReceiptRepository receiptRepository,
            TransactionRepository transactionRepository,
            VisionAiProvider visionAiProvider) {
        this.receiptRepository = receiptRepository;
        this.transactionRepository = transactionRepository;
        this.visionAiProvider = visionAiProvider;
    }

    public Receipt ingest(Long userId, MultipartFile file) {
        Receipt receipt = new Receipt();
        receipt.setUserId(userId);
        receipt = receiptRepository.save(receipt);

        try {
            byte[] imageBytes = file.getBytes();
            if (!FileSignature.isSupportedImage(imageBytes)) {
                throw new InvalidReceiptFileException(
                        "That file is not a JPEG or PNG image. Take a photo of the receipt and "
                                + "upload that.");
            }

            List<ExtractedTransaction> extracted = visionAiProvider.extractFromImage(imageBytes, file.getContentType());

            // The prompt tells the model to return an empty list when the image is not a
            // receipt. Acting on that is what turns "nothing happened" into an explanation.
            if (extracted.isEmpty()) {
                throw new UnrecognisedDocumentException(
                        "No purchase found in that image. It does not look like a receipt - "
                                + "make sure the whole slip is visible and in focus.");
            }

            for (ExtractedTransaction et : extracted) {
                transactionRepository.save(toTransaction(userId, receipt.getId(), et));
            }

            receipt.setStatus(ReceiptStatus.COMPLETE);
        } catch (InvalidReceiptFileException | UnrecognisedDocumentException ex) {
            // Mark the receipt FAILED and propagate unwrapped. Without this branch these would
            // escape the try with the row left in PROCESSING - a receipt permanently stuck
            // mid-flight because the user uploaded the wrong picture.
            receipt.setStatus(ReceiptStatus.FAILED);
            receiptRepository.save(receipt);
            throw ex;
        } catch (IOException | AllAiProvidersFailedException ex) {
            receipt.setStatus(ReceiptStatus.FAILED);
            receiptRepository.save(receipt);
            throw new ReceiptProcessingException(receipt.getId(), ex);
        }

        return receiptRepository.save(receipt);
    }

    private Transaction toTransaction(Long userId, Long receiptId, ExtractedTransaction et) {
        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setSourceType(SourceType.RECEIPT);
        transaction.setSourceId(receiptId);
        transaction.setDate(et.date());
        transaction.setMerchant(et.merchant());
        transaction.setAmount(et.amount());
        transaction.setCategory(et.category());
        transaction.setDescription(et.description());
        transaction.setPaymentMethod(parsePaymentMethod(et.paymentMethod()));
        transaction.setDirection(TransactionDirection.fromExtracted(et.direction()));
        transaction.setStatus(TransactionStatus.ACTIVE);
        return transaction;
    }

    private PaymentMethod parsePaymentMethod(String raw) {
        if (raw == null) {
            return PaymentMethod.UNKNOWN;
        }
        try {
            return PaymentMethod.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return PaymentMethod.UNKNOWN;
        }
    }
}
