package com.example.shade.service;

import com.example.shade.model.ApkLinkInvite;
import com.example.shade.repository.ApkLinkInviteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ApkLinkInviteService {

    private final ApkLinkInviteRepository inviteRepository;

    public List<ApkLinkInvite> findAllChannels() {
        return inviteRepository.findAllByTypeOrderBySortOrderAscNameAsc(ApkLinkInvite.TYPE_CHANNEL);
    }

    public List<ApkLinkInvite> findAllGroups() {
        return inviteRepository.findAllByTypeOrderBySortOrderAscNameAsc(ApkLinkInvite.TYPE_GROUP);
    }

    public Optional<ApkLinkInvite> findById(Long id) {
        return inviteRepository.findById(id);
    }

    @Transactional
    public ApkLinkInvite createChannel(String name, String inviteLink, Integer sortOrder) {
        ApkLinkInvite invite = ApkLinkInvite.builder()
                .name(name)
                .inviteLink(inviteLink != null ? inviteLink.trim() : "")
                .type(ApkLinkInvite.TYPE_CHANNEL)
                .sortOrder(sortOrder != null ? sortOrder : 0)
                .build();
        return inviteRepository.save(invite);
    }

    @Transactional
    public ApkLinkInvite createGroup(String name, String inviteLink, Integer sortOrder) {
        ApkLinkInvite invite = ApkLinkInvite.builder()
                .name(name)
                .inviteLink(inviteLink != null ? inviteLink.trim() : "")
                .type(ApkLinkInvite.TYPE_GROUP)
                .sortOrder(sortOrder != null ? sortOrder : 0)
                .build();
        return inviteRepository.save(invite);
    }

    @Transactional
    public Optional<ApkLinkInvite> updateInvite(Long id, String name, String inviteLink, Integer sortOrder) {
        return inviteRepository.findById(id).map(invite -> {
            if (name != null) invite.setName(name);
            if (inviteLink != null) invite.setInviteLink(inviteLink.trim());
            if (sortOrder != null) invite.setSortOrder(sortOrder);
            return inviteRepository.save(invite);
        });
    }

    @Transactional
    public void deleteById(Long id) {
        inviteRepository.deleteById(id);
    }
}
