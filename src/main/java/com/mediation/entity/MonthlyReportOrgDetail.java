package com.mediation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "monthly_report_org_details")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyReportOrgDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "organization_name", nullable = false)
    private String organizationName;

    @Column(name = "new_received_total")
    private Integer newReceivedTotal;

    @Column(name = "closed_total")
    private Integer closedTotal;

    @Column(name = "mediation_success")
    private Integer mediationSuccess;

    @Column(name = "success_rate", precision = 5, scale = 2)
    private BigDecimal successRate;

    @Column(name = "involved_people_total")
    private Integer involvedPeopleTotal;

    @Column(name = "involved_amount_total", precision = 15, scale = 2)
    private BigDecimal involvedAmountTotal;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
