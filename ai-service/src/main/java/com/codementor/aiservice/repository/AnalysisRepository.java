package com.codementor.aiservice.repository;

import com.codementor.aiservice.entity.AnalysisRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalysisRepository extends JpaRepository<AnalysisRequest, String> {
}