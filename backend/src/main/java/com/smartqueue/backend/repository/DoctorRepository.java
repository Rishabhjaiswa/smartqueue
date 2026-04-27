package com.smartqueue.backend.repository;

import com.smartqueue.backend.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    Optional<Doctor> findByName(String name);
    List<Doctor> findByAvailableTrue();
    List<Doctor> findByAvailableTrueAndOfficeId(Integer officeId);
    List<Doctor> findByOfficeId(Integer officeId);
    List<Doctor> findBySpecializationAndAvailableTrue(String specialization);
    List<Doctor> findBySpecializationAndAvailableTrueAndOfficeId(String specialization, Integer officeId);

    @Query("SELECT DISTINCT d.officeId FROM Doctor d ORDER BY d.officeId ASC")
    List<Integer> findDistinctOfficeIds();
}