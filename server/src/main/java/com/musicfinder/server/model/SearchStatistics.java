package com.musicfinder.server.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchStatistics {
    private Long statisticsId;
    private String keyword;
    private String normalizedKeyword;
    private String searchType;
    private Long searchCount;
    private LocalDateTime lastSearchedAt;
}