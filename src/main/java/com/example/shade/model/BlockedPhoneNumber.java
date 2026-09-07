package com.example.shade.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "blocked_phone_number")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockedPhoneNumber {

    @Id
    @Column(name = "normalized_phone", nullable = false, length = 32)
    private String normalizedPhone;

    /**
     * When set, this row was created or last tied to a chat ban; cleared on phone-only block updates as needed.
     */
    @Column(name = "linked_chat_id")
    private Long linkedChatId;
}
