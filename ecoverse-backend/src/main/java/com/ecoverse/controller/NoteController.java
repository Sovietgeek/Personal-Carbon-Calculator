package com.ecoverse.controller;

import com.ecoverse.dto.ApiResponse;
import com.ecoverse.dto.note.NoteRequest;
import com.ecoverse.dto.note.NoteResponse;
import com.ecoverse.service.NoteService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/notes")
public class NoteController {

    @Autowired
    private NoteService noteService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NoteResponse>>> getNotes() {
        Long userId = getCurrentUserId();
        List<NoteResponse> responses = noteService.getNotes(userId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NoteResponse>> createNote(@Valid @RequestBody NoteRequest request) {
        Long userId = getCurrentUserId();
        NoteResponse response = noteService.createNote(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Note created successfully", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNote(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        noteService.deleteNote(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Note deleted successfully", null));
    }

    @GetMapping("/tip")
    public ResponseEntity<ApiResponse<String>> getDailyTip() {
        String tip = noteService.getDailyTip();
        return ResponseEntity.ok(ApiResponse.success(tip));
    }

    @GetMapping("/tips/history")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getTipHistory(
            @RequestParam(defaultValue = "7") int days) {
        List<String> history = noteService.getTipHistory(days);
        List<Map<String, String>> result = new ArrayList<>();
        for (String entry : history) {
            // Each entry is in "date: tip" format
            int separatorIndex = entry.indexOf(": ");
            Map<String, String> map = new LinkedHashMap<>();
            if (separatorIndex > 0) {
                map.put("date", entry.substring(0, separatorIndex));
                map.put("tip", entry.substring(separatorIndex + 2));
            } else {
                map.put("date", "");
                map.put("tip", entry);
            }
            result.add(map);
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getPrincipal().toString());
    }
}
