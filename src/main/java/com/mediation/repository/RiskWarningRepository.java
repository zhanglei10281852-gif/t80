package com.mediation.repository;

import com.mediation.entity.RiskWarning;
import com.mediation.entity.RiskWarning.RiskLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RiskWarningRepository extends JpaRepository<RiskWarning, Long> {

    Optional<RiskWarning> findByDisputeId(Long disputeId);

    List<RiskWarning> findByRiskLevel(RiskLevel riskLevel);

    @Query("SELECT r FROM RiskWarning r WHERE r.riskLevel = :riskLevel AND r.intervened = :intervened " +
            "AND r.createdAt BETWEEN :startTime AND :endTime")
    List<RiskWarning> findByRiskLevelAndIntervenedAndCreatedAtBetween(
            @Param("riskLevel") RiskLevel riskLevel,
            @Param("intervened") Boolean intervened,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT COUNT(r) FROM RiskWarning r WHERE r.riskLevel = :riskLevel AND r.intervened = true " +
            "AND r.createdAt BETWEEN :startTime AND :endTime")
    long countPreventedEscalation(
            @Param("riskLevel") RiskLevel riskLevel,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}
