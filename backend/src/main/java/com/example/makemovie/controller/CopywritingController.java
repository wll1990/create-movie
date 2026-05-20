package com.example.makemovie.controller;

import com.example.makemovie.dto.response.CopywritingResponse;
import com.example.makemovie.service.CopywritingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/copywriting")
@RequiredArgsConstructor
public class CopywritingController {

    private final CopywritingService copywritingService;

    @PostMapping
    public ResponseEntity<CopywritingResponse> generateCopywriting(
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(copywritingService.generateCopywriting(projectId));
    }
}
