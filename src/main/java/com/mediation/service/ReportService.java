package com.mediation.service;

import com.mediation.entity.Dispute.DisputeStatus;
import com.mediation.entity.Dispute.DisputeType;
import com.mediation.entity.MonthlyReport;
import com.mediation.entity.MonthlyReport.ReportStatus;
import com.mediation.entity.MonthlyReportOrgDetail;
import com.mediation.entity.RiskWarning.RiskLevel;
import com.mediation.repository.DisputeRepository;
import com.mediation.repository.MonthlyReportOrgDetailRepository;
import com.mediation.repository.MonthlyReportRepository;
import com.mediation.repository.RiskWarningRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final DisputeRepository disputeRepository;
    private final MonthlyReportRepository monthlyReportRepository;
    private final MonthlyReportOrgDetailRepository orgDetailRepository;
    private final RiskWarningRepository riskWarningRepository;

    private static final List<DisputeStatus> CLOSED_STATUSES = Arrays.asList(
            DisputeStatus.调解成功, DisputeStatus.调解失败
    );

    private static final List<DisputeStatus> EXCLUDED_CARRY_OVER = Arrays.asList(
            DisputeStatus.调解成功, DisputeStatus.调解失败, DisputeStatus.已撤回
    );

    @Transactional
    public MonthlyReport generateMonthlyReport(Integer year, Integer month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDateTime startTime = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime endTime = yearMonth.atEndOfMonth().atTime(23, 59, 59);

        MonthlyReport existing = monthlyReportRepository.findByReportYearAndReportMonth(year, month).orElse(null);
        if (existing != null && existing.getStatus() != ReportStatus.草稿) {
            throw new IllegalStateException("报表状态为" + existing.getStatus() + "，无法重新生成");
        }

        MonthlyReport report = existing != null ? existing : new MonthlyReport();
        report.setReportYear(year);
        report.setReportMonth(month);

        int carryOver = (int) disputeRepository.countCarryOver(EXCLUDED_CARRY_OVER, startTime);
        report.setCarryOverCount(carryOver);

        Map<DisputeType, Integer> newReceivedByType = new LinkedHashMap<>();
        int newReceivedTotal = 0;
        for (DisputeType type : DisputeType.values()) {
            int count = (int) disputeRepository.countByDisputeTypeAndCreatedAtBetween(type, startTime, endTime);
            newReceivedByType.put(type, count);
            newReceivedTotal += count;
        }
        report.setNewReceivedTotal(newReceivedTotal);
        report.setNewNeighborDispute(newReceivedByType.getOrDefault(DisputeType.邻里纠纷, 0));
        report.setNewMarriageFamily(newReceivedByType.getOrDefault(DisputeType.婚姻家庭, 0));
        report.setNewLaborDispute(newReceivedByType.getOrDefault(DisputeType.劳动争议, 0));
        report.setNewContractDispute(newReceivedByType.getOrDefault(DisputeType.合同纠纷, 0));
        report.setNewDamageCompensation(newReceivedByType.getOrDefault(DisputeType.损害赔偿, 0));
        report.setNewLandOwnership(newReceivedByType.getOrDefault(DisputeType.土地权属, 0));
        report.setNewOtherDispute(newReceivedByType.getOrDefault(DisputeType.其他, 0));

        Map<DisputeType, Integer> closedByType = new LinkedHashMap<>();
        int closedTotal = 0;
        for (DisputeType type : DisputeType.values()) {
            int count = (int) disputeRepository.countClosedByDisputeType(type, CLOSED_STATUSES, startTime, endTime);
            closedByType.put(type, count);
            closedTotal += count;
        }
        report.setClosedTotal(closedTotal);
        report.setClosedNeighborDispute(closedByType.getOrDefault(DisputeType.邻里纠纷, 0));
        report.setClosedMarriageFamily(closedByType.getOrDefault(DisputeType.婚姻家庭, 0));
        report.setClosedLaborDispute(closedByType.getOrDefault(DisputeType.劳动争议, 0));
        report.setClosedContractDispute(closedByType.getOrDefault(DisputeType.合同纠纷, 0));
        report.setClosedDamageCompensation(closedByType.getOrDefault(DisputeType.损害赔偿, 0));
        report.setClosedLandOwnership(closedByType.getOrDefault(DisputeType.土地权属, 0));
        report.setClosedOtherDispute(closedByType.getOrDefault(DisputeType.其他, 0));

        int successCount = (int) disputeRepository.countByStatusAndUpdatedAtBetween(
                DisputeStatus.调解成功, startTime, endTime);
        report.setMediationSuccess(successCount);

        BigDecimal successRate = closedTotal > 0
                ? BigDecimal.valueOf(successCount).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(closedTotal), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        report.setSuccessRate(successRate);

        Integer involvedPeople = disputeRepository.sumInvolvedPeopleByStatusAndUpdatedAtBetween(
                CLOSED_STATUSES, startTime, endTime);
        report.setInvolvedPeopleTotal(involvedPeople != null ? involvedPeople : 0);

        BigDecimal involvedAmount = disputeRepository.sumAmountByStatusAndUpdatedAtBetween(
                CLOSED_STATUSES, startTime, endTime);
        report.setInvolvedAmountTotal(involvedAmount != null ? involvedAmount : BigDecimal.ZERO);

        long preventedEscalation = riskWarningRepository.countPreventedEscalation(
                RiskLevel.极高风险, startTime, endTime);
        report.setPreventedEscalationCount((int) preventedEscalation);

        long massPetitionSuccess = disputeRepository.countMassPetitionByStatusAndUpdatedAtBetween(
                DisputeStatus.调解成功, startTime, endTime);
        report.setPreventedMassPetitionCount((int) massPetitionSuccess);

        MonthlyReport saved = monthlyReportRepository.save(report);

        generateOrgDetails(saved.getId(), startTime, endTime);

        calculateMoMAndYoY(saved);

        return monthlyReportRepository.save(saved);
    }

    private void generateOrgDetails(Long reportId, LocalDateTime startTime, LocalDateTime endTime) {
        orgDetailRepository.deleteByReportId(reportId);

        Map<String, Integer> newReceivedMap = new HashMap<>();
        List<Object[]> newReceivedList = disputeRepository.countNewReceivedByOrganization(startTime, endTime);
        for (Object[] row : newReceivedList) {
            newReceivedMap.put((String) row[0], ((Number) row[1]).intValue());
        }

        Map<String, Integer> closedMap = new HashMap<>();
        List<Object[]> closedList = disputeRepository.countClosedByOrganization(CLOSED_STATUSES, startTime, endTime);
        for (Object[] row : closedList) {
            closedMap.put((String) row[0], ((Number) row[1]).intValue());
        }

        Map<String, Integer> successMap = new HashMap<>();
        List<Object[]> successList = disputeRepository.countSuccessByOrganization(
                DisputeStatus.调解成功, startTime, endTime);
        for (Object[] row : successList) {
            successMap.put((String) row[0], ((Number) row[1]).intValue());
        }

        Map<String, Integer> peopleMap = new HashMap<>();
        List<Object[]> peopleList = disputeRepository.sumInvolvedPeopleByOrganization(CLOSED_STATUSES, startTime, endTime);
        for (Object[] row : peopleList) {
            peopleMap.put((String) row[0], ((Number) row[1]).intValue());
        }

        Map<String, BigDecimal> amountMap = new HashMap<>();
        List<Object[]> amountList = disputeRepository.sumAmountByOrganization(CLOSED_STATUSES, startTime, endTime);
        for (Object[] row : amountList) {
            amountMap.put((String) row[0], (BigDecimal) row[1]);
        }

        Set<String> allOrgs = new HashSet<>();
        allOrgs.addAll(newReceivedMap.keySet());
        allOrgs.addAll(closedMap.keySet());
        allOrgs.addAll(successMap.keySet());

        List<MonthlyReportOrgDetail> details = new ArrayList<>();
        for (String org : allOrgs) {
            int closed = closedMap.getOrDefault(org, 0);
            int success = successMap.getOrDefault(org, 0);
            BigDecimal rate = closed > 0
                    ? BigDecimal.valueOf(success).multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(closed), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            MonthlyReportOrgDetail detail = MonthlyReportOrgDetail.builder()
                    .reportId(reportId)
                    .organizationName(org)
                    .newReceivedTotal(newReceivedMap.getOrDefault(org, 0))
                    .closedTotal(closed)
                    .mediationSuccess(success)
                    .successRate(rate)
                    .involvedPeopleTotal(peopleMap.getOrDefault(org, 0))
                    .involvedAmountTotal(amountMap.getOrDefault(org, BigDecimal.ZERO))
                    .build();
            details.add(detail);
        }

        orgDetailRepository.saveAll(details);
    }

    private void calculateMoMAndYoY(MonthlyReport report) {
        YearMonth current = YearMonth.of(report.getReportYear(), report.getReportMonth());

        YearMonth prevMonth = current.minusMonths(1);
        MonthlyReport prevReport = monthlyReportRepository
                .findByReportYearAndReportMonth(prevMonth.getYear(), prevMonth.getMonthValue())
                .orElse(null);
        if (prevReport != null && prevReport.getNewReceivedTotal() != null
                && prevReport.getNewReceivedTotal() > 0) {
            BigDecimal mom = BigDecimal.valueOf(report.getNewReceivedTotal() - prevReport.getNewReceivedTotal())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(prevReport.getNewReceivedTotal()), 2, RoundingMode.HALF_UP);
            report.setMonthOverMonthRate(mom);
            if (mom.abs().compareTo(BigDecimal.valueOf(50)) > 0) {
                report.setDataAbnormal(true);
            }
        } else {
            report.setMonthOverMonthRate(null);
        }

        YearMonth lastYear = current.minusYears(1);
        MonthlyReport lastYearReport = monthlyReportRepository
                .findByReportYearAndReportMonth(lastYear.getYear(), lastYear.getMonthValue())
                .orElse(null);
        if (lastYearReport != null && lastYearReport.getNewReceivedTotal() != null
                && lastYearReport.getNewReceivedTotal() > 0) {
            BigDecimal yoy = BigDecimal.valueOf(report.getNewReceivedTotal() - lastYearReport.getNewReceivedTotal())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(lastYearReport.getNewReceivedTotal()), 2, RoundingMode.HALF_UP);
            report.setYearOverYearRate(yoy);
        } else {
            report.setYearOverYearRate(null);
        }
    }

    public List<String> validateReport(Long reportId) {
        MonthlyReport report = monthlyReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("报表不存在"));

        List<String> errors = new ArrayList<>();

        int totalReceived = (report.getCarryOverCount() != null ? report.getCarryOverCount() : 0)
                + (report.getNewReceivedTotal() != null ? report.getNewReceivedTotal() : 0);
        int closed = report.getClosedTotal() != null ? report.getClosedTotal() : 0;
        if (closed > totalReceived) {
            errors.add("办结纠纷数(" + closed + ")不能大于受理数(" + totalReceived + "，含上月结转)");
        }

        int success = report.getMediationSuccess() != null ? report.getMediationSuccess() : 0;
        if (success > closed) {
            errors.add("调解成功数(" + success + ")不能大于办结数(" + closed + ")");
        }

        if (closed > 0 && (report.getInvolvedPeopleTotal() == null || report.getInvolvedPeopleTotal() == 0)) {
            errors.add("有办结案件时，涉及人数不能为0");
        }

        return errors;
    }

    @Transactional
    public MonthlyReport confirmReport(Long reportId, String operator) {
        MonthlyReport report = monthlyReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("报表不存在"));

        if (report.getStatus() != ReportStatus.草稿) {
            throw new IllegalStateException("只有草稿状态的报表才能确认");
        }

        List<String> errors = validateReport(reportId);
        if (!errors.isEmpty()) {
            throw new IllegalStateException("数据校验不通过：" + String.join("; ", errors));
        }

        report.setStatus(ReportStatus.已确认);
        report.setConfirmedBy(operator);
        report.setConfirmedAt(LocalDateTime.now());

        return monthlyReportRepository.save(report);
    }

    @Transactional
    public MonthlyReport reportSubmitted(Long reportId, String operator) {
        MonthlyReport report = monthlyReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("报表不存在"));

        if (report.getStatus() != ReportStatus.已确认) {
            throw new IllegalStateException("只有已确认状态的报表才能上报");
        }

        report.setStatus(ReportStatus.已上报);
        report.setReportedBy(operator);
        report.setReportedAt(LocalDateTime.now());

        return monthlyReportRepository.save(report);
    }

    @Transactional
    public MonthlyReport updateReportFields(Long reportId, Map<String, Object> updates) {
        MonthlyReport report = monthlyReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("报表不存在"));

        if (report.getStatus() != ReportStatus.草稿) {
            throw new IllegalStateException("只有草稿状态的报表才能修改");
        }

        updates.forEach((key, value) -> {
            switch (key) {
                case "carryOverCount":
                    report.setCarryOverCount((Integer) value);
                    break;
                case "newReceivedTotal":
                    report.setNewReceivedTotal((Integer) value);
                    break;
                case "closedTotal":
                    report.setClosedTotal((Integer) value);
                    break;
                case "mediationSuccess":
                    report.setMediationSuccess((Integer) value);
                    break;
                case "successRate":
                    report.setSuccessRate((BigDecimal) value);
                    break;
                case "involvedPeopleTotal":
                    report.setInvolvedPeopleTotal((Integer) value);
                    break;
                case "involvedAmountTotal":
                    report.setInvolvedAmountTotal((BigDecimal) value);
                    break;
                case "preventedEscalationCount":
                    report.setPreventedEscalationCount((Integer) value);
                    break;
                case "preventedMassPetitionCount":
                    report.setPreventedMassPetitionCount((Integer) value);
                    break;
                case "remark":
                    report.setRemark((String) value);
                    break;
            }
        });

        return monthlyReportRepository.save(report);
    }

    public Map<String, Object> generateQuarterlyReport(Integer year, Integer quarter) {
        int startMonth = (quarter - 1) * 3 + 1;
        int endMonth = quarter * 3;

        List<MonthlyReport> monthlyReports = monthlyReportRepository.findByYearAndMonthRange(
                year, startMonth, endMonth);

        return aggregateReports(monthlyReports, year + "年第" + quarter + "季度");
    }

    public Map<String, Object> generateAnnualReport(Integer year) {
        List<MonthlyReport> monthlyReports = monthlyReportRepository.findByReportYearOrderByReportMonth(year);
        return aggregateReports(monthlyReports, year + "年度");
    }

    private Map<String, Object> aggregateReports(List<MonthlyReport> reports, String periodName) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("periodName", periodName);

        int carryOver = reports.stream().findFirst()
                .map(MonthlyReport::getCarryOverCount).orElse(0);
        result.put("carryOverCount", carryOver);

        int newReceivedTotal = reports.stream()
                .mapToInt(r -> r.getNewReceivedTotal() != null ? r.getNewReceivedTotal() : 0).sum();
        result.put("newReceivedTotal", newReceivedTotal);

        result.put("newNeighborDispute",
                reports.stream().mapToInt(r -> r.getNewNeighborDispute() != null ? r.getNewNeighborDispute() : 0).sum());
        result.put("newMarriageFamily",
                reports.stream().mapToInt(r -> r.getNewMarriageFamily() != null ? r.getNewMarriageFamily() : 0).sum());
        result.put("newLaborDispute",
                reports.stream().mapToInt(r -> r.getNewLaborDispute() != null ? r.getNewLaborDispute() : 0).sum());
        result.put("newContractDispute",
                reports.stream().mapToInt(r -> r.getNewContractDispute() != null ? r.getNewContractDispute() : 0).sum());
        result.put("newDamageCompensation",
                reports.stream().mapToInt(r -> r.getNewDamageCompensation() != null ? r.getNewDamageCompensation() : 0).sum());
        result.put("newLandOwnership",
                reports.stream().mapToInt(r -> r.getNewLandOwnership() != null ? r.getNewLandOwnership() : 0).sum());
        result.put("newOtherDispute",
                reports.stream().mapToInt(r -> r.getNewOtherDispute() != null ? r.getNewOtherDispute() : 0).sum());

        int closedTotal = reports.stream()
                .mapToInt(r -> r.getClosedTotal() != null ? r.getClosedTotal() : 0).sum();
        result.put("closedTotal", closedTotal);

        result.put("closedNeighborDispute",
                reports.stream().mapToInt(r -> r.getClosedNeighborDispute() != null ? r.getClosedNeighborDispute() : 0).sum());
        result.put("closedMarriageFamily",
                reports.stream().mapToInt(r -> r.getClosedMarriageFamily() != null ? r.getClosedMarriageFamily() : 0).sum());
        result.put("closedLaborDispute",
                reports.stream().mapToInt(r -> r.getClosedLaborDispute() != null ? r.getClosedLaborDispute() : 0).sum());
        result.put("closedContractDispute",
                reports.stream().mapToInt(r -> r.getClosedContractDispute() != null ? r.getClosedContractDispute() : 0).sum());
        result.put("closedDamageCompensation",
                reports.stream().mapToInt(r -> r.getClosedDamageCompensation() != null ? r.getClosedDamageCompensation() : 0).sum());
        result.put("closedLandOwnership",
                reports.stream().mapToInt(r -> r.getClosedLandOwnership() != null ? r.getClosedLandOwnership() : 0).sum());
        result.put("closedOtherDispute",
                reports.stream().mapToInt(r -> r.getClosedOtherDispute() != null ? r.getClosedOtherDispute() : 0).sum());

        int successTotal = reports.stream()
                .mapToInt(r -> r.getMediationSuccess() != null ? r.getMediationSuccess() : 0).sum();
        result.put("mediationSuccess", successTotal);

        BigDecimal successRate = closedTotal > 0
                ? BigDecimal.valueOf(successTotal).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(closedTotal), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        result.put("successRate", successRate);

        int peopleTotal = reports.stream()
                .mapToInt(r -> r.getInvolvedPeopleTotal() != null ? r.getInvolvedPeopleTotal() : 0).sum();
        result.put("involvedPeopleTotal", peopleTotal);

        BigDecimal amountTotal = reports.stream()
                .map(r -> r.getInvolvedAmountTotal() != null ? r.getInvolvedAmountTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        result.put("involvedAmountTotal", amountTotal);

        result.put("preventedEscalationCount",
                reports.stream().mapToInt(r -> r.getPreventedEscalationCount() != null ? r.getPreventedEscalationCount() : 0).sum());
        result.put("preventedMassPetitionCount",
                reports.stream().mapToInt(r -> r.getPreventedMassPetitionCount() != null ? r.getPreventedMassPetitionCount() : 0).sum());

        result.put("monthCount", reports.size());

        return result;
    }

    public Map<String, Object> compareReports(Long reportId1, Long reportId2) {
        MonthlyReport r1 = monthlyReportRepository.findById(reportId1)
                .orElseThrow(() -> new IllegalArgumentException("报表1不存在"));
        MonthlyReport r2 = monthlyReportRepository.findById(reportId2)
                .orElseThrow(() -> new IllegalArgumentException("报表2不存在"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("report1", r1.getReportYear() + "-" + r1.getReportMonth());
        result.put("report2", r2.getReportYear() + "-" + r2.getReportMonth());

        Map<String, Map<String, Object>> differences = new LinkedHashMap<>();

        addDiff(differences, "newReceivedTotal",
                r1.getNewReceivedTotal(), r2.getNewReceivedTotal());
        addDiff(differences, "closedTotal",
                r1.getClosedTotal(), r2.getClosedTotal());
        addDiff(differences, "mediationSuccess",
                r1.getMediationSuccess(), r2.getMediationSuccess());
        addDiff(differences, "successRate",
                r1.getSuccessRate(), r2.getSuccessRate());
        addDiff(differences, "involvedPeopleTotal",
                r1.getInvolvedPeopleTotal(), r2.getInvolvedPeopleTotal());
        addDiff(differences, "involvedAmountTotal",
                r1.getInvolvedAmountTotal(), r2.getInvolvedAmountTotal());
        addDiff(differences, "preventedEscalationCount",
                r1.getPreventedEscalationCount(), r2.getPreventedEscalationCount());
        addDiff(differences, "preventedMassPetitionCount",
                r1.getPreventedMassPetitionCount(), r2.getPreventedMassPetitionCount());

        result.put("differences", differences);
        return result;
    }

    private void addDiff(Map<String, Map<String, Object>> diffs, String field, Number v1, Number v2) {
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("value1", v1);
        diff.put("value2", v2);

        if (v1 != null && v2 != null) {
            if (v1 instanceof BigDecimal || v2 instanceof BigDecimal) {
                BigDecimal bd1 = new BigDecimal(v1.toString());
                BigDecimal bd2 = new BigDecimal(v2.toString());
                diff.put("change", bd2.subtract(bd1));
                if (bd1.compareTo(BigDecimal.ZERO) != 0) {
                    diff.put("changeRate", bd2.subtract(bd1).multiply(BigDecimal.valueOf(100))
                            .divide(bd1, 2, RoundingMode.HALF_UP));
                }
            } else {
                long change = v2.longValue() - v1.longValue();
                diff.put("change", change);
                if (v1.longValue() != 0) {
                    diff.put("changeRate", BigDecimal.valueOf(change).multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(v1.longValue()), 2, RoundingMode.HALF_UP));
                }
            }
        }

        diffs.put(field, diff);
    }

    public Map<String, Object> getYearlyTrend(Integer year) {
        List<MonthlyReport> reports = monthlyReportRepository.findByReportYearOrderByReportMonth(year);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", year);

        List<String> months = new ArrayList<>();
        List<Integer> newReceivedData = new ArrayList<>();
        List<Integer> closedData = new ArrayList<>();
        List<BigDecimal> successRateData = new ArrayList<>();

        for (int i = 1; i <= 12; i++) {
            months.add(i + "月");
            int monthIdx = i;
            Optional<MonthlyReport> reportOpt = reports.stream()
                    .filter(r -> r.getReportMonth() == monthIdx).findFirst();
            if (reportOpt.isPresent()) {
                MonthlyReport r = reportOpt.get();
                newReceivedData.add(r.getNewReceivedTotal() != null ? r.getNewReceivedTotal() : 0);
                closedData.add(r.getClosedTotal() != null ? r.getClosedTotal() : 0);
                successRateData.add(r.getSuccessRate() != null ? r.getSuccessRate() : BigDecimal.ZERO);
            } else {
                newReceivedData.add(0);
                closedData.add(0);
                successRateData.add(BigDecimal.ZERO);
            }
        }

        result.put("months", months);
        result.put("newReceivedData", newReceivedData);
        result.put("closedData", closedData);
        result.put("successRateData", successRateData);

        return result;
    }

    public Map<String, Object> getYoYGrowthTrend(Integer year) {
        List<MonthlyReport> currentYear = monthlyReportRepository.findByReportYearOrderByReportMonth(year);
        List<MonthlyReport> lastYear = monthlyReportRepository.findByReportYearOrderByReportMonth(year - 1);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", year);
        result.put("compareYear", year - 1);

        List<String> months = new ArrayList<>();
        List<BigDecimal> yoyRates = new ArrayList<>();

        for (int i = 1; i <= 12; i++) {
            months.add(i + "月");
            int monthIdx = i;
            Optional<MonthlyReport> currOpt = currentYear.stream()
                    .filter(r -> r.getReportMonth() == monthIdx).findFirst();
            Optional<MonthlyReport> lastOpt = lastYear.stream()
                    .filter(r -> r.getReportMonth() == monthIdx).findFirst();

            if (currOpt.isPresent() && lastOpt.isPresent()
                    && lastOpt.get().getNewReceivedTotal() != null
                    && lastOpt.get().getNewReceivedTotal() > 0) {
                int currVal = currOpt.get().getNewReceivedTotal() != null
                        ? currOpt.get().getNewReceivedTotal() : 0;
                int lastVal = lastOpt.get().getNewReceivedTotal();
                BigDecimal rate = BigDecimal.valueOf(currVal - lastVal).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(lastVal), 2, RoundingMode.HALF_UP);
                yoyRates.add(rate);
            } else {
                yoyRates.add(null);
            }
        }

        result.put("months", months);
        result.put("yoyRates", yoyRates);

        return result;
    }

    public Map<String, Object> getOrganizationContribution(Integer year, Integer month) {
        MonthlyReport report = monthlyReportRepository.findByReportYearAndReportMonth(year, month)
                .orElseThrow(() -> new IllegalArgumentException("月报不存在"));

        List<MonthlyReportOrgDetail> details = orgDetailRepository
                .findByReportIdOrderByNewReceivedTotalDesc(report.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", year);
        result.put("month", month);

        List<String> orgNames = new ArrayList<>();
        List<Integer> newReceivedCounts = new ArrayList<>();
        List<BigDecimal> percentages = new ArrayList<>();

        int total = report.getNewReceivedTotal() != null ? report.getNewReceivedTotal() : 0;

        for (MonthlyReportOrgDetail detail : details) {
            orgNames.add(detail.getOrganizationName());
            newReceivedCounts.add(detail.getNewReceivedTotal());
            if (total > 0) {
                BigDecimal pct = BigDecimal.valueOf(detail.getNewReceivedTotal()).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
                percentages.add(pct);
            } else {
                percentages.add(BigDecimal.ZERO);
            }
        }

        result.put("organizations", orgNames);
        result.put("newReceivedCounts", newReceivedCounts);
        result.put("percentages", percentages);

        return result;
    }
}
