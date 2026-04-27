package com.smartqueue.backend.config;

import com.smartqueue.backend.entity.Doctor;
import com.smartqueue.backend.entity.StaffUser;
import com.smartqueue.backend.repository.DoctorRepository;
import com.smartqueue.backend.repository.StaffUserRepository;
import com.smartqueue.backend.service.StaffUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class SystemBootstrapInitializer implements CommandLineRunner {

    private final DoctorRepository doctorRepository;
    private final StaffUserRepository staffUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final Optional<RedisTemplate<String, String>> redisTemplate;
    private final StaffUserService staffUserService;

    @Value("${app.redis.required:false}")
    private boolean redisRequired;

    @Value("${smartqueue.reset-on-startup:false}")
    private boolean resetOnStartup;

    @Value("${clinic.redis.queue-prefix:queue:doctor:}")
    private String queuePrefix;

    @Value("${clinic.redis.consultation-key:consultation:doctor:}")
    private String consultationKeyPrefix;

    @Value("${clinic.redis.load-key:doctor:load:}")
    private String loadKeyPrefix;

    @Override
    public void run(String... args) {
        if (redisRequired && redisTemplate.isEmpty()) {
            throw new IllegalStateException("Redis required but not available");
        }

        if (redisTemplate.isPresent()) {
            log.info("Redis status: CONNECTED");
        } else {
            log.warn("Redis status: DISABLED (running in degraded mode)");
        }

        if (!resetOnStartup) {
            seedDefaultsIfMissing();
            return;
        }

        clearRedisKeys(queuePrefix + "*");
        clearRedisKeys(consultationKeyPrefix + "*");
        clearRedisKeys(loadKeyPrefix + "*");
        seedDefaultsIfMissing();
    }

    private void seedDefaultsIfMissing() {
        seedDoctors();
        seedAdminUser();
    }

    // ─── Doctors ──────────────────────────────────────────────────────────────

    private static final List<Object[]> DOCTOR_SEED = List.of(
        new Object[]{"Dr. Aryan Sharma",   "CARDIOLOGY",  "101", 10, "doc.cardio"},
        new Object[]{"Dr. Priya Mehta",    "PEDIATRICS",  "102", 10, "doc.pedia"},
        new Object[]{"Dr. Sameer Khan",    "DERMATOLOGY", "103", 10, "doc.derm"},
        new Object[]{"Dr. Anita Deshmukh", "ORTHOPEDICS", "104", 10, "doc.ortho"},
        new Object[]{"Dr. Rahul Varma",    "GENERAL",     "105", 10, "doc.general"}
    );

    private void seedDoctors() {
        for (Object[] row : DOCTOR_SEED) {
            String name           = (String) row[0];
            String specialization = (String) row[1];
            String room           = (String) row[2];
            int    avgMins        = (int)    row[3];
            String username       = (String) row[4];

            Doctor doctor = doctorRepository.findByName(name).orElseGet(() -> {
                Doctor d = Doctor.builder()
                        .name(name)
                        .specialization(specialization)
                        .roomNumber(room)
                        .available(true)
                        .avgConsultMins(avgMins)
                        .maxQueueSize(25)
                        .officeId(1)
                        .build();
                d = doctorRepository.save(d);
                log.info("Seeded doctor: {} ({})", name, specialization);
                return d;
            });

            staffUserRepository.findByUsername(username).orElseGet(() -> {
                StaffUser su = StaffUser.builder()
                        .username(username)
                        .password(passwordEncoder.encode("SmartDoc@2026"))
                        .role("DOCTOR")
                        .officeId(1)
                        .doctorId(doctor.getId())
                        .build();
                staffUserRepository.save(su);
                log.info("Seeded staff account: {}", username);
                return su;
            });
        }
    }

    // ─── Admin ────────────────────────────────────────────────────────────────

    private void seedAdminUser() {
        staffUserRepository.findByUsername("admin").orElseGet(() -> {
            StaffUser admin = StaffUser.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("Admin@SmartQueue"))
                    .role("ADMIN")
                    .officeId(1)
                    .doctorId(null)
                    .build();
            staffUserRepository.save(admin);
            log.info("Seeded admin user");
            return admin;
        });
    }

    // ─── Redis helpers ────────────────────────────────────────────────────────

    private void clearRedisKeys(String pattern) {
        if (redisTemplate.isEmpty()) {
            log.warn("Redis disabled, skipping key clearance: {}", pattern);
            return;
        }
        try {
            Set<String> keys = redisTemplate.get().keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.get().delete(keys);
            }
        } catch (Exception e) {
            log.warn("Unable to clear Redis keys for {}: {}", pattern, e.getMessage());
        }
    }
}
