package com.mediation.repository;

import com.mediation.entity.MonthlyReport;
import com.mediation.entity.MonthlyReport.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MonthlyReportRepository extends JpaRepository<MonthlyReport, Long> {

    Optional<MonthlyReport> findByReportYearAndReportMonth(Integer year, Integer month);

    List<MonthlyReport> findByReportYearOrderByReportMonth(Integer year);

    Page<MonthlyReport> findAllByOrderByReportYearDescReportMonthDesc(Pageable pageable);

    @Query("SELECT m FROM MonthlyReport m WHERE m.reportYear = :year AND m.reportMonth BETWEEN :startMonth AND :endMonth ORDER BY m.reportMonth")
    List<MonthlyReport> findByYearAndMonthRange(
            @Param("year") Integer year,
            @Param("startMonth") Integer startMonth,
            @Param("endMonth") Integer endMonth);

    @Query("SELECT m FROM MonthlyReport m WHERE m.reportYear BETWEEN :startYear AND :endYear ORDER BY m.reportYear, m.reportMonth")
    List<MonthlyReport> findByYearRange(
            @Param("startYear") Integer startYear,
            @Param("endYear") Integer endYear);

    boolean existsByReportYearAndReportMonth(Integer year, Integer month);

    List<MonthlyReport> findByStatus(ReportStatus status);
}
