package com.medbridge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An external system that sends patient records (LAB, CLINIC, HOSPITAL).
 * Its format decides which parser reads its files. Seeded by data.sql.
 */
@Entity
@Table(name = "source_system")
@Getter
@Setter
@NoArgsConstructor
public class SourceSystem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 10)
    private SourceFormat format;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
