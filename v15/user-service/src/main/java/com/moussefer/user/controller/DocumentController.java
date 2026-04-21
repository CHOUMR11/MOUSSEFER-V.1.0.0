package com.moussefer.user.controller;

import com.moussefer.user.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadDocument(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("type") String documentType,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(documentService.uploadDocument(userId, documentType, file));
    }
}