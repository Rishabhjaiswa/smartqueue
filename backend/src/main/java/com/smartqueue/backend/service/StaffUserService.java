package com.smartqueue.backend.service;

import com.smartqueue.backend.dto.CreateStaffRequest;
import com.smartqueue.backend.dto.StaffUserDTO;
import com.smartqueue.backend.entity.Doctor;
import com.smartqueue.backend.entity.StaffUser;
import com.smartqueue.backend.repository.DoctorRepository;
import com.smartqueue.backend.repository.StaffUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class StaffUserService implements UserDetailsService {

    private final StaffUserRepository staffUserRepository;
    private final DoctorRepository doctorRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info("AUTH ATTEMPT: {}", username);
        StaffUser staffUser = getStaffUser(username);
        log.info("DB USER FOUND: {}", staffUser.getUsername());

        return User.builder()
                .username(staffUser.getUsername())
                .password(staffUser.getPassword())
                .authorities(new SimpleGrantedAuthority(toAuthority(staffUser.getRole())))
                .build();
    }

    public Long getDoctorId(String username) {
        StaffUser staffUser = getStaffUser(username);

        if (staffUser.getDoctorId() == null) {
            throw new IllegalArgumentException("Doctor mapping not configured for this user");
        }

        return staffUser.getDoctorId();
    }

    public Map<String, Object> getUserProfile(String username) {
        StaffUser staffUser = getStaffUser(username);
        Doctor doctor = staffUser.getDoctorId() != null
                ? doctorRepository.findById(staffUser.getDoctorId()).orElse(null)
                : null;

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("username", staffUser.getUsername());
        profile.put("role", toAuthority(staffUser.getRole()));
        profile.put("doctorId", staffUser.getDoctorId());
        profile.put("name", doctor != null ? doctor.getName() : staffUser.getUsername());
        profile.put("doctorName", doctor != null ? doctor.getName() : "");
        profile.put("available", doctor != null && doctor.isAvailable());
        return profile;
    }

    @Transactional
    public StaffUserDTO createStaff(CreateStaffRequest request) {
        String username = request.getUsername().trim();
        String role = normalizeRole(request.getRole());

        if (staffUserRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }

        Long doctorId = null;
        if ("DOCTOR".equals(role)) {
            if (request.getDoctorId() == null && (request.getDoctorName() == null || request.getDoctorName().isBlank())) {
                throw new IllegalArgumentException("Doctor mapping is required for doctor role");
            }
            if (request.getDoctorId() != null) {
                doctorId = doctorRepository.findById(request.getDoctorId())
                        .orElseThrow(() -> new IllegalArgumentException("Doctor not found"))
                        .getId();
            } else {
                Doctor doctor = doctorRepository.save(
                        Doctor.builder()
                                .name(request.getDoctorName().trim())
                                .specialization(request.getSpecialization() == null || request.getSpecialization().isBlank()
                                        ? "General Medicine" : request.getSpecialization().trim())
                                .roomNumber(request.getRoomNumber() == null || request.getRoomNumber().isBlank()
                                        ? "Room" : request.getRoomNumber().trim())
                                .available(true)
                                .avgConsultMins(request.getAvgConsultMins() == null || request.getAvgConsultMins() <= 0
                                        ? 10 : request.getAvgConsultMins())
                                .maxQueueSize(25)
                                .build()
                );
                doctorId = doctor.getId();
            }

            if (staffUserRepository.existsByDoctorId(doctorId)) {
                throw new IllegalArgumentException("This doctor is already mapped to another login");
            }
        }

        StaffUser saved = staffUserRepository.save(
                StaffUser.builder()
                        .username(username)
                        .password(passwordEncoder.encode(request.getPassword()))
                        .officeId(1)
                        .role(role)
                        .doctorId(doctorId)
                        .build()
        );
        log.info("AUDIT admin-create-staff username={} role={} doctorId={}", saved.getUsername(), saved.getRole(), saved.getDoctorId());
        auditLogService.log(
                "CREATE_STAFF",
                "admin",
                "Created staff user " + saved.getUsername() + " with role " + saved.getRole()
                        + (saved.getDoctorId() != null ? " mapped to doctor " + saved.getDoctorId() : "")
        );

        return toDto(saved);
    }

    public List<StaffUserDTO> listStaff() {
        return staffUserRepository.findAllByOrderByUsernameAsc()
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void resetAdminPassword() {
        StaffUser admin = staffUserRepository.findByUsername("admin")
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        admin.setPassword(passwordEncoder.encode("admin@123"));
        admin.setRole("ADMIN");
        admin.setOfficeId(1);
        admin.setDoctorId(null);
        staffUserRepository.save(admin);
    }

    @Transactional
    public void resetStaffPassword(Long staffUserId, String newPassword) {
        StaffUser staffUser = staffUserRepository.findById(staffUserId)
                .orElseThrow(() -> new IllegalArgumentException("Staff user not found"));

        staffUser.setPassword(passwordEncoder.encode(newPassword));
        staffUserRepository.save(staffUser);
        auditLogService.log(
                "RESET_PASSWORD",
                "admin",
                "Password reset for user " + staffUser.getUsername()
        );
    }

    private StaffUser getStaffUser(String username) {
        return staffUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    private String normalizeRole(String rawRole) {
        if (rawRole == null || rawRole.isBlank()) {
            throw new IllegalArgumentException("Role is required");
        }

        String normalized = rawRole.trim().toUpperCase();
        if ("RECEPTION".equals(normalized)) {
            return "RECEPTIONIST";
        }
        if (!List.of("ADMIN", "DOCTOR", "RECEPTIONIST").contains(normalized)) {
            throw new IllegalArgumentException("Invalid role");
        }
        return normalized;
    }

    private String toAuthority(String role) {
        return role.startsWith("ROLE_") ? role : "ROLE_" + role;
    }

    private StaffUserDTO toDto(StaffUser staffUser) {
        Doctor doctor = staffUser.getDoctorId() != null
                ? doctorRepository.findById(staffUser.getDoctorId()).orElse(null)
                : null;

        return StaffUserDTO.builder()
                .id(staffUser.getId())
                .username(staffUser.getUsername())
                .role(staffUser.getRole())
                .doctorId(staffUser.getDoctorId())
                .doctorName(doctor != null ? doctor.getName() : null)
                .build();
    }
}
