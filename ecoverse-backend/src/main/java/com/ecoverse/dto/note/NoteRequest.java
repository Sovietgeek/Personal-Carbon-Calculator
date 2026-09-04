package com.ecoverse.dto.note;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoteRequest {

    @NotBlank(message = "Title is required")
    @Size(min = 1, max = 200, message = "Title must be between 1 and 200 characters")
    private String title;

    @NotBlank(message = "Body is required")
    @Size(min = 1, max = 10000, message = "Body must be between 1 and 10000 characters")
    private String body;

    @Size(max = 50, message = "Tag must be at most 50 characters")
    private String tag;
}
