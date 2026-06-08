package com.mediation.repository;

import com.mediation.entity.MonthlyReportOrgDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MonthlyReportOrgDetailRepository extends JpaRepository<MonthlyReportOrgDetail, Long> {

    List<MonthlyReportOrgDetail> findByReportIdOrderByNewReceivedTotalDesc(Long reportId);

    void deleteByReportId(Long reportId);
}
