package com.mediation.controller;

import com.mediation.entity.MonthlyReport;
import com.mediation.entity.MonthlyReport.ReportStatus;
import com.mediation.entity.MonthlyReportOrgDetail;
import com.mediation.repository.MonthlyReportOrgDetailRepository;
import com.mediation.repository.MonthlyReportRepository;
import com.mediation.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final MonthlyReportRepository monthlyReportRepository;
    private final MonthlyReportOrgDetailRepository orgDetailRepository;

    @PostMapping("/monthly/generate")
    public ResponseEntity<?> generateMonthlyReport(@RequestBody Map<String, Integer> body) {
        Integer year = body.get("year");
        Integer month = body.get("month");
        if (year == null || month == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "年份和月份不能为空"));
        }
        try {
            MonthlyReport report = reportService.generateMonthlyReport(year, month);
            return ResponseEntity.ok(report);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/monthly")
    public ResponseEntity<Page<MonthlyReport>> listMonthlyReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<MonthlyReport> result = monthlyReportRepository
                .findAllByOrderByReportYearDescReportMonthDesc(pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/monthly/{id}")
    public ResponseEntity<?> getMonthlyReport(@PathVariable Long id) {
        Optional<MonthlyReport> reportOpt = monthlyReportRepository.findById(id);
        if (reportOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        MonthlyReport report = reportOpt.get();
        List<MonthlyReportOrgDetail> orgDetails = orgDetailRepository
                .findByReportIdOrderByNewReceivedTotalDesc(id);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("report", report);
        result.put("orgDetails", orgDetails);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/monthly/by-date")
    public ResponseEntity<?> getMonthlyReportByDate(
            @RequestParam Integer year,
            @RequestParam Integer month) {
        Optional<MonthlyReport> reportOpt = monthlyReportRepository
                .findByReportYearAndReportMonth(year, month);
        if (reportOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        MonthlyReport report = reportOpt.get();
        List<MonthlyReportOrgDetail> orgDetails = orgDetailRepository
                .findByReportIdOrderByNewReceivedTotalDesc(report.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("report", report);
        result.put("orgDetails", orgDetails);

        return ResponseEntity.ok(result);
    }

    @PutMapping("/monthly/{id}")
    public ResponseEntity<?> updateMonthlyReport(
            @PathVariable Long id,
            @RequestBody Map<String, Object> updates) {
        try {
            MonthlyReport report = reportService.updateReportFields(id, updates);
            return ResponseEntity.ok(report);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/monthly/{id}/confirm")
    public ResponseEntity<?> confirmReport(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String operator = body != null ? body.get("operator") : "admin";
            MonthlyReport report = reportService.confirmReport(id, operator);
            return ResponseEntity.ok(report);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/monthly/{id}/submit")
    public ResponseEntity<?> submitReport(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String operator = body != null ? body.get("operator") : "admin";
            MonthlyReport report = reportService.reportSubmitted(id, operator);
            return ResponseEntity.ok(report);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/monthly/{id}/validate")
    public ResponseEntity<?> validateReport(@PathVariable Long id) {
        try {
            List<String> errors = reportService.validateReport(id);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("valid", errors.isEmpty());
            result.put("errors", errors);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/quarterly")
    public ResponseEntity<?> getQuarterlyReport(
            @RequestParam Integer year,
            @RequestParam Integer quarter) {
        if (quarter < 1 || quarter > 4) {
            return ResponseEntity.badRequest().body(Map.of("error", "季度必须是1-4"));
        }
        Map<String, Object> report = reportService.generateQuarterlyReport(year, quarter);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/annual")
    public ResponseEntity<?> getAnnualReport(@RequestParam Integer year) {
        Map<String, Object> report = reportService.generateAnnualReport(year);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/compare")
    public ResponseEntity<?> compareReports(
            @RequestParam Long reportId1,
            @RequestParam Long reportId2) {
        try {
            Map<String, Object> result = reportService.compareReports(reportId1, reportId2);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/monthly/{id}/export")
    public ResponseEntity<byte[]> exportMonthlyReportCsv(@PathVariable Long id) {
        Optional<MonthlyReport> reportOpt = monthlyReportRepository.findById(id);
        if (reportOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        MonthlyReport report = reportOpt.get();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);

            writer.write("\uFEFF");

            writer.write("报表项目,数值\n");

            writer.write("报表周期," + report.getReportYear() + "年" + report.getReportMonth() + "月\n");
            writer.write("上月结转," + nvl(report.getCarryOverCount()) + "\n");
            writer.write("新收纠纷总数," + nvl(report.getNewReceivedTotal()) + "\n");
            writer.write("  邻里纠纷," + nvl(report.getNewNeighborDispute()) + "\n");
            writer.write("  婚姻家庭," + nvl(report.getNewMarriageFamily()) + "\n");
            writer.write("  劳动争议," + nvl(report.getNewLaborDispute()) + "\n");
            writer.write("  合同纠纷," + nvl(report.getNewContractDispute()) + "\n");
            writer.write("  损害赔偿," + nvl(report.getNewDamageCompensation()) + "\n");
            writer.write("  土地权属," + nvl(report.getNewLandOwnership()) + "\n");
            writer.write("  其他," + nvl(report.getNewOtherDispute()) + "\n");

            writer.write("办结纠纷总数," + nvl(report.getClosedTotal()) + "\n");
            writer.write("  邻里纠纷," + nvl(report.getClosedNeighborDispute()) + "\n");
            writer.write("  婚姻家庭," + nvl(report.getClosedMarriageFamily()) + "\n");
            writer.write("  劳动争议," + nvl(report.getClosedLaborDispute()) + "\n");
            writer.write("  合同纠纷," + nvl(report.getClosedContractDispute()) + "\n");
            writer.write("  损害赔偿," + nvl(report.getClosedDamageCompensation()) + "\n");
            writer.write("  土地权属," + nvl(report.getClosedLandOwnership()) + "\n");
            writer.write("  其他," + nvl(report.getClosedOtherDispute()) + "\n");

            writer.write("调解成功数," + nvl(report.getMediationSuccess()) + "\n");
            writer.write("调解成功率(%)," + nvl(report.getSuccessRate()) + "\n");
            writer.write("涉及人数合计," + nvl(report.getInvolvedPeopleTotal()) + "\n");
            writer.write("涉及金额合计(元)," + nvl(report.getInvolvedAmountTotal()) + "\n");
            writer.write("防止民间纠纷激化数," + nvl(report.getPreventedEscalationCount()) + "\n");
            writer.write("防止群体性上访数," + nvl(report.getPreventedMassPetitionCount()) + "\n");
            writer.write("环比增长率(%)," + nvl(report.getMonthOverMonthRate()) + "\n");
            writer.write("同比增长率(%)," + nvl(report.getYearOverYearRate()) + "\n");
            writer.write("数据异常," + (report.getDataAbnormal() ? "是" : "否") + "\n");
            writer.write("报表状态," + report.getStatus() + "\n");
            writer.write("备注," + (report.getRemark() != null ? report.getRemark() : "") + "\n");

            writer.write("\n");
            writer.write("各调解组织明细\n");
            writer.write("组织名称,新收数,办结数,成功数,成功率(%),涉及人数,涉及金额(元)\n");

            List<MonthlyReportOrgDetail> orgDetails = orgDetailRepository
                    .findByReportIdOrderByNewReceivedTotalDesc(id);

            int totalNew = 0, totalClosed = 0, totalSuccess = 0, totalPeople = 0;
            BigDecimal totalAmount = BigDecimal.ZERO;

            for (MonthlyReportOrgDetail detail : orgDetails) {
                writer.write(detail.getOrganizationName() + ",");
                writer.write(nvl(detail.getNewReceivedTotal()) + ",");
                writer.write(nvl(detail.getClosedTotal()) + ",");
                writer.write(nvl(detail.getMediationSuccess()) + ",");
                writer.write(nvl(detail.getSuccessRate()) + ",");
                writer.write(nvl(detail.getInvolvedPeopleTotal()) + ",");
                writer.write(nvl(detail.getInvolvedAmountTotal()) + "\n");

                totalNew += detail.getNewReceivedTotal() != null ? detail.getNewReceivedTotal() : 0;
                totalClosed += detail.getClosedTotal() != null ? detail.getClosedTotal() : 0;
                totalSuccess += detail.getMediationSuccess() != null ? detail.getMediationSuccess() : 0;
                totalPeople += detail.getInvolvedPeopleTotal() != null ? detail.getInvolvedPeopleTotal() : 0;
                if (detail.getInvolvedAmountTotal() != null) {
                    totalAmount = totalAmount.add(detail.getInvolvedAmountTotal());
                }
            }

            writer.write("合计," + totalNew + "," + totalClosed + "," + totalSuccess + ",");
            BigDecimal totalRate = totalClosed > 0
                    ? BigDecimal.valueOf(totalSuccess).multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(totalClosed), 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            writer.write(totalRate + "," + totalPeople + "," + totalAmount + "\n");

            writer.flush();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
            String filename = report.getReportYear() + "年" + report.getReportMonth() + "月调解工作月报.csv";
            headers.setContentDispositionFormData("attachment",
                    new String(filename.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));

            return new ResponseEntity<>(baos.toByteArray(), headers, HttpStatus.OK);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/stats/yearly-trend")
    public ResponseEntity<?> getYearlyTrend(@RequestParam Integer year) {
        Map<String, Object> result = reportService.getYearlyTrend(year);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/stats/yoy-growth")
    public ResponseEntity<?> getYoYGrowthTrend(@RequestParam Integer year) {
        Map<String, Object> result = reportService.getYoYGrowthTrend(year);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/stats/org-contribution")
    public ResponseEntity<?> getOrganizationContribution(
            @RequestParam Integer year,
            @RequestParam Integer month) {
        try {
            Map<String, Object> result = reportService.getOrganizationContribution(year, month);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private String nvl(Object value) {
        if (value == null) {
            return "0";
        }
        return value.toString();
    }
}
