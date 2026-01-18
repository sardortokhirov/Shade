package com.example.shade.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserSummaryDTO {
    private Long totalTopUps;
    private Long totalTransfers;
    private Long totalRequests;
    private Long approvedRequests;
    private Long canceledRequests;
    private Long pendingRequests;
    private Long failedRequests;
    private LocalDateTime firstRequestDate;
    private LocalDateTime lastRequestDate;
}
