package com.example.shade.repository;

import com.example.shade.model.ApkLinkPlatform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApkLinkPlatformRepository extends JpaRepository<ApkLinkPlatform, Long> {

    List<ApkLinkPlatform> findAllByOrderBySortOrderAscNameAsc();
}
