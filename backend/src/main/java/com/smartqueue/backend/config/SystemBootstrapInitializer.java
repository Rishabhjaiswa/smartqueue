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

        System.out.println(
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                        .matches(
                                "admin@123",
                                "$2a$10$VRlsBIhZCAsTiMZXRUAAje.01d647EwgbP/iBvQR7lggeyqzhHEoa"
                        )
        );
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
        if (doctorRepository.count() == 0) {
            doctorRepository.saveAll(List.of(
                    Doctor.builder()
                            .name("Dr. Sharma")
                            .specialization("General Medicine")
                            .roomNumber("101")
                            .available(true)
                            .avgConsultMins(7)
                            .maxQueueSize(25)
                            .build(),
                    Doctor.builder()
                            .name("Dr. Mehta")
                            .specialization("Internal Medicine")
                            .roomNumber("102")
                            .available(true)
                            .avgConsultMins(9)
                            .maxQueueSize(25)
                            .build(),
                    Doctor.builder()
                            .name("Dr. Iyer")
                            .specialization("Family Medicine")
                            .roomNumber("103")
                            .available(true)
                            .avgConsultMins(6)
                            .maxQueueSize(25)
                            .build()
            ));
        }

        StaffUser admin = staffUserRepository.findByUsername("admin")
                .orElse(null);

        if (admin == null) {
            admin = StaffUser.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin@123"))
                    .role("ADMIN")
                    .officeId(1)
                    .doctorId(null)
                    .build();
        } else {
            admin.setPassword(passwordEncoder.encode("admin@123"));
            admin.setRole("ADMIN");
            admin.setOfficeId(1);
            admin.setDoctorId(null);
        }

        staffUserRepository.save(admin);
        log.info("ADMIN PASSWORD RESET TO admin@123");
    }

    private void clearRedisKeys(String pattern) {
        if (redisTemplate.isEmpty()) {
            log.warn("Redis is disabled, skipping key clearance for: {}", pattern);
            return;
        }
        try {
            Set<String> keys = redisTemplate.get().keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.get().delete(keys);
            }
        } catch (Exception e) {
            log.warn("Unable to clear Redis keys for pattern {}: {}", pattern, e.getMessage());
        }
    }
}
