package com.smartqueue.backend.repository;

import com.smartqueue.backend.entity.StaffUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffUserRepository extends JpaRepository<StaffUser, Long> {
    Optional<StaffUser> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByDoctorId(Long doctorId);
    List<StaffUser> findAllByOrderByUsernameAsc();
}
