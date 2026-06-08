package com.mediation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "monthly_reports", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"report_year", "report_month"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_year", nullable = false)
    private Integer reportYear;

    @Column(name = "report_month", nullable = false)
    private Integer reportMonth;

    @Column(name = "carry_over_count")
    private Integer carryOverCount;

    @Column(name = "new_received_total")
    private Integer newReceivedTotal;

    @Column(name = "new_neighbor_dispute")
    private Integer newNeighborDispute;

    @Column(name = "new_marriage_family")
    private Integer newMarriageFamily;

    @Column(name = "new_labor_dispute")
    private Integer newLaborDispute;

    @Column(name = "new_contract_dispute")
    private Integer newContractDispute;

    @Column(name = "new_damage_compensation")
    private Integer newDamageCompensation;

    @Column(name = "new_land_ownership")
    private Integer newLandOwnership;

    @Column(name = "new_other_dispute")
    private Integer newOtherDispute;

    @Column(name = "closed_total")
    private Integer closedTotal;

    @Column(name = "closed_neighbor_dispute")
    private Integer closedNeighborDispute;

    @Column(name = "closed_marriage_family")
    private Integer closedMarriageFamily;

    @Column(name = "closed_labor_dispute")
    private Integer closedLaborDispute;

    @Column(name = "closed_contract_dispute")
    private Integer closedContractDispute;

    @Column(name = "closed_damage_compensation")
    private Integer closedDamageCompensation;

    @Column(name = "closed_land_ownership")
    private Integer closedLandOwnership;

    @Column(name = "closed_other_dispute")
    private Integer closedOtherDispute;

    @Column(name = "mediation_success")
    private Integer mediationSuccess;

    @Column(name = "success_rate", precision = 5, scale = 2)
    private BigDecimal successRate;

    @Column(name = "involved_people_total")
    private Integer involvedPeopleTotal;

    @Column(name = "involved_amount_total", precision = 15, scale = 2)
    private BigDecimal involvedAmountTotal;

    @Column(name = "prevented_escalation_count")
    private Integer preventedEscalationCount;

    @Column(name = "prevented_mass_petition_count")
    private Integer preventedMassPetitionCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status;

    @Column(name = "month_over_month_rate", precision = 7, scale = 2)
    private BigDecimal monthOverMonthRate;

    @Column(name = "year_over_year_rate", precision = 7, scale = 2)
    private BigDecimal yearOverYearRate;

    @Column(name = "data_abnormal")
    private Boolean dataAbnormal;

    @Column(columnDefinition = "TEXT")
    private String remark;

    @Column(name = "confirmed_by")
    private String confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "reported_by")
    private String reportedBy;

    @Column(name = "reported_at")
    private LocalDateTime reportedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = ReportStatus.草稿;
        }
        if (this.dataAbnormal == null) {
            this.dataAbnormal = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum ReportStatus {
        草稿, 已确认, 已上报
    }
}
