package com.smartqueue.backend.repository;

import com.smartqueue.backend.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    // Legacy single-patient lookup (kept for Telegram flow)
    Optional<Patient> findFirstByPhone(String phone);

    // Phase 2: Family identification — all members sharing a phone
    List<Patient> findByPhone(String phone);

    // Phase 2: Exact match for phone + name (case-insensitive)
    Optional<Patient> findByPhoneAndNameIgnoreCase(String phone, String name);

    // Telegram lookups
    Optional<Patient> findByTelegramChatId(Long chatId);
    List<Patient> findAllByTelegramChatIdOrderByCreatedAtAsc(Long chatId);
    Optional<Patient> findByTelegramChatIdAndNameIgnoreCaseAndAge(Long chatId, String name, Integer age);
}
