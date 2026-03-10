package com.example.schemasync.repository;

import com.example.schemasync.entity.SchemaDiff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SchemaDiffRepository extends JpaRepository<SchemaDiff, Long> {
    List<SchemaDiff> findByStatus(SchemaDiff.Status status);
}
