package com.example.shade.repository;

import com.example.shade.model.ApkLinkKeyword;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApkLinkKeywordRepository extends JpaRepository<ApkLinkKeyword, Long> {

    List<ApkLinkKeyword> findByPlatformIdOrderByKeywordAsc(Long platformId);

    Optional<ApkLinkKeyword> findByPlatformIdAndKeywordIgnoreCase(Long platformId, String keyword);

    boolean existsByPlatformIdAndKeywordIgnoreCase(Long platformId, String keyword);

    Optional<ApkLinkKeyword> findFirstByKeywordIgnoreCase(String keyword);
}
