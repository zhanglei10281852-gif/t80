package com.mediation.repository;

import com.mediation.entity.Dispute;
import com.mediation.entity.Dispute.DisputeStatus;
import com.mediation.entity.Dispute.DisputeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, Long> {

    Page<Dispute> findByStatus(DisputeStatus status, Pageable pageable);

    Page<Dispute> findByMediatorId(Long mediatorId, Pageable pageable);

    Page<Dispute> findByDisputeType(DisputeType disputeType, Pageable pageable);

    @Query("SELECT d FROM Dispute d WHERE d.applicantName LIKE %:keyword%")
    Page<Dispute> searchByApplicantName(@Param("keyword") String keyword, Pageable pageable);

    long countByStatus(DisputeStatus status);

    long countByDisputeType(DisputeType disputeType);

    @Query("SELECT COUNT(d) FROM Dispute d WHERE d.disputeType = :type AND d.createdAt BETWEEN :startTime AND :endTime")
    long countByDisputeTypeAndCreatedAtBetween(
            @Param("type") DisputeType type,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT COUNT(d) FROM Dispute d WHERE d.status = :status AND d.createdAt BETWEEN :startTime AND :endTime")
    long countByStatusAndCreatedAtBetween(
            @Param("status") DisputeStatus status,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT COUNT(d) FROM Dispute d WHERE d.disputeType = :type AND d.status IN :statuses AND d.updatedAt BETWEEN :startTime AND :endTime")
    long countClosedByDisputeType(
            @Param("type") DisputeType type,
            @Param("statuses") List<DisputeStatus> statuses,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT COUNT(d) FROM Dispute d WHERE d.status = :status AND d.updatedAt BETWEEN :startTime AND :endTime")
    long countByStatusAndUpdatedAtBetween(
            @Param("status") DisputeStatus status,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT COALESCE(SUM(d.involvedPeopleCount), 0) FROM Dispute d WHERE d.status IN :statuses AND d.updatedAt BETWEEN :startTime AND :endTime")
    Integer sumInvolvedPeopleByStatusAndUpdatedAtBetween(
            @Param("statuses") List<DisputeStatus> statuses,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM Dispute d WHERE d.status IN :statuses AND d.updatedAt BETWEEN :startTime AND :endTime")
    BigDecimal sumAmountByStatusAndUpdatedAtBetween(
            @Param("statuses") List<DisputeStatus> statuses,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT COUNT(d) FROM Dispute d WHERE d.isMassPetition = true AND d.status = :status AND d.updatedAt BETWEEN :startTime AND :endTime")
    long countMassPetitionByStatusAndUpdatedAtBetween(
            @Param("status") DisputeStatus status,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT COUNT(d) FROM Dispute d WHERE d.status NOT IN :excludedStatuses AND d.createdAt < :endTime")
    long countCarryOver(
            @Param("excludedStatuses") List<DisputeStatus> excludedStatuses,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT m.organization, COUNT(d) FROM Dispute d JOIN Mediator m ON d.mediatorId = m.id " +
            "WHERE d.createdAt BETWEEN :startTime AND :endTime GROUP BY m.organization")
    List<Object[]> countNewReceivedByOrganization(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT m.organization, COUNT(d) FROM Dispute d JOIN Mediator m ON d.mediatorId = m.id " +
            "WHERE d.status IN :statuses AND d.updatedAt BETWEEN :startTime AND :endTime GROUP BY m.organization")
    List<Object[]> countClosedByOrganization(
            @Param("statuses") List<DisputeStatus> statuses,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT m.organization, COUNT(d) FROM Dispute d JOIN Mediator m ON d.mediatorId = m.id " +
            "WHERE d.status = :status AND d.updatedAt BETWEEN :startTime AND :endTime GROUP BY m.organization")
    List<Object[]> countSuccessByOrganization(
            @Param("status") DisputeStatus status,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT m.organization, COALESCE(SUM(d.involvedPeopleCount), 0) FROM Dispute d JOIN Mediator m ON d.mediatorId = m.id " +
            "WHERE d.status IN :statuses AND d.updatedAt BETWEEN :startTime AND :endTime GROUP BY m.organization")
    List<Object[]> sumInvolvedPeopleByOrganization(
            @Param("statuses") List<DisputeStatus> statuses,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT m.organization, COALESCE(SUM(d.amount), 0) FROM Dispute d JOIN Mediator m ON d.mediatorId = m.id " +
            "WHERE d.status IN :statuses AND d.updatedAt BETWEEN :startTime AND :endTime GROUP BY m.organization")
    List<Object[]> sumAmountByOrganization(
            @Param("statuses") List<DisputeStatus> statuses,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}
