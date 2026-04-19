package com.smartqueue.backend.repository;

import com.smartqueue.backend.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    List<Doctor> findByAvailableTrue();
    List<Doctor> findBySpecializationAndAvailableTrue(String specialization);
}