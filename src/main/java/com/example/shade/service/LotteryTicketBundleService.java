package com.example.shade.service;

import com.example.shade.model.LotteryTicketBundle;
import com.example.shade.repository.LotteryTicketBundleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LotteryTicketBundleService {
    private final LotteryTicketBundleRepository bundleRepository;

    public List<LotteryTicketBundle> getActiveBundles() {
        return bundleRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
    }

    @Transactional
    public LotteryTicketBundle save(LotteryTicketBundle bundle) {
        if (bundle.getCreatedAt() == null) {
            bundle.setCreatedAt(LocalDateTime.now());
        }
        return bundleRepository.save(bundle);
    }

    @Transactional
    public void delete(Long id) {
        bundleRepository.deleteById(id);
    }

    @Transactional
    public LotteryTicketBundle toggleActive(Long id) {
        LotteryTicketBundle bundle = bundleRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Bundle not found: " + id));
        bundle.setIsActive(!bundle.getIsActive());
        return bundleRepository.save(bundle);
    }

    public LotteryTicketBundle findById(Long id) {
        return bundleRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Bundle not found: " + id));
    }
}
