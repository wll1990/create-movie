package com.example.makemovie.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopywritingResponse {
    private String title;
    private String description;
    private List<String> hashtags;
    private String coverDescription;
}
