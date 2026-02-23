package com.example.shade.repository;

import com.example.shade.model.ApkLinkInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApkLinkInviteRepository extends JpaRepository<ApkLinkInvite, Long> {

    List<ApkLinkInvite> findAllByTypeOrderBySortOrderAscNameAsc(String type);
}
