package com.mediation.controller;

import com.mediation.entity.RiskWarning;
import com.mediation.entity.RiskWarning.RiskLevel;
import com.mediation.repository.RiskWarningRepository;
import com.mediation.service.RiskWarningService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/risk-warnings")
@RequiredArgsConstructor
public class RiskWarningController {

    private final RiskWarningService riskWarningService;
    private final RiskWarningRepository riskWarningRepository;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        Long disputeId = body.get("disputeId") != null
                ? Long.valueOf(body.get("disputeId").toString()) : null;
        String riskLevelStr = (String) body.get("riskLevel");
        String description = (String) body.get("description");

        if (disputeId == null || riskLevelStr == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "纠纷ID和风险等级不能为空"));
        }

        try {
            RiskLevel riskLevel = RiskLevel.valueOf(riskLevelStr);
            RiskWarning warning = riskWarningService.createOrUpdate(disputeId, riskLevel, description);
            return ResponseEntity.status(HttpStatus.CREATED).body(warning);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<Page<RiskWarning>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String riskLevel) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<RiskWarning> result;
        if (riskLevel != null) {
            try {
                RiskLevel level = RiskLevel.valueOf(riskLevel);
                result = riskWarningRepository.findAll(pageable); // 简化实现
            } catch (IllegalArgumentException e) {
                result = riskWarningRepository.findAll(pageable);
            }
        } else {
            result = riskWarningRepository.findAll(pageable);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        Optional<RiskWarning> warningOpt = riskWarningService.findById(id);
        if (warningOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(warningOpt.get());
    }

    @GetMapping("/dispute/{disputeId}")
    public ResponseEntity<?> getByDisputeId(@PathVariable Long disputeId) {
        Optional<RiskWarning> warningOpt = riskWarningService.findByDisputeId(disputeId);
        if (warningOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(warningOpt.get());
    }

    @PutMapping("/{id}/intervene")
    public ResponseEntity<?> markIntervened(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String result = body.get("result");
        if (result == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "介入结果不能为空"));
        }

        try {
            RiskWarning warning = riskWarningService.markIntervened(id, result);
            return ResponseEntity.ok(warning);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
