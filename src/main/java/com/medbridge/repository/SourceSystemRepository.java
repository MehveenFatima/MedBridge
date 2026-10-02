package com.medbridge.repository;

import com.medbridge.entity.SourceSystem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SourceSystemRepository extends JpaRepository<SourceSystem, Long> {

    Optional<SourceSystem> findByCode(String code);
}
