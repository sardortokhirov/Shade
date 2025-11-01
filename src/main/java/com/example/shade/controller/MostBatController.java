package com.example.shade.controller;

import com.example.shade.model.LotteryPrize;
import com.example.shade.service.MostbetService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mostbet")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MostBatController {

    private final MostbetService mostbetService;

    @GetMapping("/profile")
    public ResponseEntity<Void> getPrizes(HttpServletRequest request) throws Exception {
        mostbetService.confirmCashout("api-key:36005f90-644a-4a56-8002-7ec52a26a257","c0514a94-a420-4080-bed8-b7336808ae81","108393","D54472E4",19179278);
        return ResponseEntity.ok().build();
    }
}
