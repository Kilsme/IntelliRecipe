package com.IntelliRecipe.Kilsme.aiService;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VectorTextChunk {
    private String id;
    private String source;
    private String title;
    private String text;
    private double[] vector;
    private double score;
}

