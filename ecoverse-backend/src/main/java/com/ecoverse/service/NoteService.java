package com.ecoverse.service;

import com.ecoverse.dto.note.NoteRequest;
import com.ecoverse.dto.note.NoteResponse;
import com.ecoverse.exception.ForbiddenException;
import com.ecoverse.exception.ResourceNotFoundException;
import com.ecoverse.model.Note;
import com.ecoverse.repository.NoteRepository;
import com.ecoverse.util.InputSanitizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NoteService {

    @Autowired
    private NoteRepository noteRepository;

    private static final String[] TIPS = {
            "Switch to LED bulbs – they use 75% less energy than incandescent lighting",
            "Unplug devices when not in use – standby power accounts for 5-10% of household energy",
            "Take shorter showers – cutting just 2 minutes saves 10 gallons of water",
            "Bring reusable bags – a single plastic bag takes 500 years to degrade",
            "Choose public transport – it reduces CO2 emissions by 45% per passenger mile",
            "Eat one plant-based meal a day – it saves 1,200 kg CO2 per year",
            "Compost food scraps – it reduces methane emissions from landfills",
            "Use a reusable water bottle – it saves an average of 156 plastic bottles per year",
            "Wash clothes in cold water – 90% of washing machine energy goes to heating water",
            "Buy local produce – it reduces food miles and supports local farmers",
            "Turn off lights when leaving a room – save 0.5 kg CO2 per bulb per day",
            "Use a clothesline instead of a dryer – save 2 kg CO2 per load",
            "Reduce meat consumption – livestock produces 14.5% of global greenhouse gases",
            "Plant a tree – one tree absorbs about 22 kg of CO2 per year",
            "Carpool to work – sharing with one person cuts your commute emissions in half",
            "Use both sides of paper – it saves 1 kg CO2 per 500 sheets",
            "Choose energy-efficient appliances – they use 10-50% less energy",
            "Fix leaky faucets – a drip wastes 3,000 gallons per year",
            "Walk or bike for trips under 3 km – zero emissions and great for health",
            "Switch to e-statements – save 6 kg CO2 per year per account",
            "Support renewable energy – consider green energy plans from your utility",
            "Reduce food waste – plan meals to avoid throwing away 1.3 billion tons annually",
            "Recycle – one ton of recycled paper saves 17 trees and 2.3 m³ of landfill"
    };

    public List<NoteResponse> getNotes(Long userId) {
        List<Note> notes = noteRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return notes.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public NoteResponse createNote(Long userId, NoteRequest req) {
        // Sanitize inputs
        String title = InputSanitizer.sanitize(req.getTitle(), InputSanitizer.MAX_TITLE_LENGTH);
        String body = InputSanitizer.sanitizeText(req.getBody());
        String tag = InputSanitizer.sanitize(req.getTag(), 50);

        Note note = Note.builder()
                .userId(userId)
                .title(title)
                .body(body)
                .tag(tag)
                .build();

        note = noteRepository.save(note);
        return mapToResponse(note);
    }

    public void deleteNote(Long noteId, Long userId) {
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("Note", "id", noteId));

        // Ownership check — ForbiddenException for unauthorized access
        if (!note.getUserId().equals(userId)) {
            throw new ForbiddenException("You don't have access to this note");
        }

        noteRepository.deleteByIdAndUserId(noteId, userId);
    }

    public String getDailyTip() {
        int dayOfYear = LocalDate.now().getDayOfYear();
        return TIPS[dayOfYear % TIPS.length];
    }

    public List<String> getTipHistory(int days) {
        List<String> history = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 0; i < days; i++) {
            LocalDate date = today.minusDays(i);
            int dayOfYear = date.getDayOfYear();
            String tip = TIPS[dayOfYear % TIPS.length];
            history.add(date + ": " + tip);
        }
        return history;
    }

    private NoteResponse mapToResponse(Note note) {
        return NoteResponse.builder()
                .id(note.getId())
                .title(note.getTitle())
                .body(note.getBody())
                .tag(note.getTag())
                .createdAt(note.getCreatedAt())
                .build();
    }
}
