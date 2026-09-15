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
import com.finme.backend.exception.ReceiptNotFoundException;
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
    private final BackgroundRunner backgroundRunner;

    public ReceiptIngestionService(
            ReceiptRepository receiptRepository,
            TransactionRepository transactionRepository,
            VisionAiProvider visionAiProvider,
            BackgroundRunner backgroundRunner) {
        this.receiptRepository = receiptRepository;
        this.transactionRepository = transactionRepository;
        this.visionAiProvider = visionAiProvider;
        this.backgroundRunner = backgroundRunner;
    }

    /**
     * Records the receipt and hands the vision call to the background, mirroring what
     * {@link StatementIngestionService#ingest} does for statements.
     * <p>
     * It used to run the whole pipeline inline and return only once the vision provider had
     * answered, so the browser held an open request for the entire call - seconds at best,
     * and longer whenever the provider chain had to fall through to its second or third
     * option. Statements never behaved that way; receipts were the odd one out.
     * <p>
     * The file is read into memory here rather than inside the task on purpose: MultipartFile
     * is backed by the request, which is gone once this method returns.
     */
    public Receipt ingest(Long userId, MultipartFile file) {
        byte[] imageBytes;
        try {
            imageBytes = file.getBytes();
        } catch (IOException ex) {
            throw new InvalidReceiptFileException("That file could not be read. Try uploading it again.");
        }

        // Checked before returning, not in the background: this needs no AI call, and a
        // wrong file type is worth rejecting outright rather than reporting as a failed
        // receipt the user has to go looking for.
        if (!FileSignature.isSupportedImage(imageBytes)) {
            throw new InvalidReceiptFileException(
                    "That file is not a JPEG or PNG image. Take a photo of the receipt and "
                            + "upload that.");
        }

        Receipt receipt = new Receipt();
        receipt.setUserId(userId);
        receipt = receiptRepository.save(receipt);

        Long receiptId = receipt.getId();
        String contentType = file.getContentType();
        backgroundRunner.run("receipt " + receiptId,
                () -> process(receiptId, userId, imageBytes, contentType));
        return receipt;
    }

    /**
     * Runs the vision extraction for an already-recorded receipt, leaving it COMPLETE or
     * FAILED. Synchronous and public so tests can call it directly, and so the background task
     * {@link #ingest} schedules has something to invoke.
     * <p>
     * Nothing is waiting on the return value once this runs in the background, so failures are
     * recorded on the row rather than thrown - failureReason is the only way the user finds
     * out why.
     */
    public Receipt process(Long receiptId, Long userId, byte[] imageBytes, String contentType) {
        Receipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new ReceiptProcessingException(receiptId, null));

        try {
            List<ExtractedTransaction> extracted = visionAiProvider.extractFromImage(imageBytes, contentType);

            // The prompt tells the model to return an empty list when the image is not a
            // receipt. Acting on that is what turns "nothing happened" into an explanation.
            if (extracted.isEmpty()) {
                throw new UnrecognisedDocumentException(
                        "No purchase found in that image. It does not look like a receipt - "
                                + "make sure the whole slip is visible and in focus.");
            }

            for (ExtractedTransaction et : extracted) {
                transactionRepository.save(toTransaction(userId, receiptId, et));
            }

            receipt.setStatus(ReceiptStatus.COMPLETE);
        } catch (UnrecognisedDocumentException ex) {
            return fail(receipt, ex.getMessage());
        } catch (AllAiProvidersFailedException ex) {
            return fail(receipt, "Could not read that receipt right now. Try again in a few minutes.");
        }

        return receiptRepository.save(receipt);
    }

    /**
     * Filtering on userId is the authorization check, not a convenience: without it any signed-in
     * user could poll any receipt id and read back another person's upload status.
     */
    public Receipt getForUser(Long userId, Long receiptId) {
        return receiptRepository.findById(receiptId)
                .filter(receipt -> receipt.getUserId().equals(userId))
                .orElseThrow(() -> new ReceiptNotFoundException(receiptId));
    }

    private Receipt fail(Receipt receipt, String reason) {
        receipt.setStatus(ReceiptStatus.FAILED);
        // Truncated to the column width - an overlong provider message would otherwise fail
        // the write and lose the reason entirely.
        receipt.setFailureReason(reason != null && reason.length() > 512 ? reason.substring(0, 512) : reason);
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
