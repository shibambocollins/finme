package com.finme.backend.controller;

import com.finme.backend.security.AuthenticatedUser;
import com.finme.backend.dto.BankStatementResponse;
import com.finme.backend.entity.BankStatement;
import com.finme.backend.exception.InvalidStatementFileException;
import com.finme.backend.service.StatementIngestionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/statements")
public class StatementController {

    private final StatementIngestionService statementIngestionService;
    private final AuthenticatedUser authenticatedUser;

    public StatementController(StatementIngestionService statementIngestionService, AuthenticatedUser authenticatedUser) {
        this.statementIngestionService = statementIngestionService;
        this.authenticatedUser = authenticatedUser;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BankStatementResponse> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidStatementFileException("Uploaded file is empty");
        }
        if (!MediaType.APPLICATION_PDF_VALUE.equals(file.getContentType())) {
            throw new InvalidStatementFileException("Only PDF files are supported");
        }

        BankStatement statement = statementIngestionService.ingest(authenticatedUser.currentUserId(), file);
        return ResponseEntity.status(HttpStatus.CREATED).body(BankStatementResponse.from(statement));
    }
}
