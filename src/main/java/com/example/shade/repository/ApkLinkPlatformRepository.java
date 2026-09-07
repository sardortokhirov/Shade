package com.example.shade.repository;

import com.example.shade.model.ApkLinkPlatform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApkLinkPlatformRepository extends JpaRepository<ApkLinkPlatform, Long> {

    List<ApkLinkPlatform> findAllByOrderBySortOrderAscNameAsc();

    Optional<ApkLinkPlatform> findByLinkKeywordIgnoreCase(String linkKeyword);

    Optional<ApkLinkPlatform> findByApkKeywordIgnoreCase(String apkKeyword);
}
