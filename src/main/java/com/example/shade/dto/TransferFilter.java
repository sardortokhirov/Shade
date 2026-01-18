package com.example.shade.dto;

import com.example.shade.model.RequestStatus;
import com.example.shade.model.RequestType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransferFilter {
    private RequestStatus status;
    private String platform;
    private RequestType type;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
