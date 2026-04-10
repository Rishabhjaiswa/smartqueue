package com.smartqueue.backend.repository;

import com.smartqueue.backend.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByPhone(String phone);

    Optional<Patient> findByTelegramChatId(Long chatId);
}