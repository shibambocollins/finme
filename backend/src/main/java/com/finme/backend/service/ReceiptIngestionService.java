package com.finme.backend.service;

import com.finme.backend.ai.AllAiProvidersFailedException;
import com.finme.backend.ai.ExtractedTransaction;
import com.finme.backend.ai.VisionAiProvider;
import com.finme.backend.entity.PaymentMethod;
import com.finme.backend.entity.Receipt;
import com.finme.backend.entity.ReceiptStatus;
import com.finme.backend.entity.SourceType;
import com.finme.backend.entity.Transaction;
import com.finme.backend.entity.TransactionStatus;
import com.finme.backend.exception.ReceiptProcessingException;
import com.finme.backend.geocoding.TransactionGeocoder;
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
    private final TransactionGeocoder transactionGeocoder;

    public ReceiptIngestionService(
            ReceiptRepository receiptRepository,
            TransactionRepository transactionRepository,
            VisionAiProvider visionAiProvider,
            TransactionGeocoder transactionGeocoder) {
        this.receiptRepository = receiptRepository;
        this.transactionRepository = transactionRepository;
        this.visionAiProvider = visionAiProvider;
        this.transactionGeocoder = transactionGeocoder;
    }

    public Receipt ingest(Long userId, MultipartFile file) {
        Receipt receipt = new Receipt();
        receipt.setUserId(userId);
        receipt = receiptRepository.save(receipt);

        try {
            byte[] imageBytes = file.getBytes();
            List<ExtractedTransaction> extracted = visionAiProvider.extractFromImage(imageBytes, file.getContentType());

            for (ExtractedTransaction et : extracted) {
                transactionRepository.save(toTransaction(userId, receipt.getId(), et));
            }

            receipt.setStatus(ReceiptStatus.COMPLETE);
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
        transaction.setStatus(TransactionStatus.ACTIVE);
        transactionGeocoder.enrich(transaction, et);
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
