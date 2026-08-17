package com.codementor.codeservice.repository;

import com.codementor.codeservice.entity.AnalysisRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalysisRepository extends JpaRepository<AnalysisRequest, String> {
}