package com.codementor.codeservice.repository;

import com.codementor.codeservice.entity.AnalysisTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AnalysisTaskRepository extends JpaRepository<AnalysisTask, UUID> {
}
