package com.mediation.service;

import com.mediation.entity.Dispute;
import com.mediation.entity.RiskWarning;
import com.mediation.entity.RiskWarning.RiskLevel;
import com.mediation.repository.DisputeRepository;
import com.mediation.repository.RiskWarningRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RiskWarningService {

    private final RiskWarningRepository riskWarningRepository;
    private final DisputeRepository disputeRepository;

    @Transactional
    public RiskWarning createOrUpdate(Long disputeId, RiskLevel riskLevel, String description) {
        Optional<Dispute> disputeOpt = disputeRepository.findById(disputeId);
        if (disputeOpt.isEmpty()) {
            throw new IllegalArgumentException("纠纷案件不存在");
        }

        RiskWarning warning = riskWarningRepository.findByDisputeId(disputeId)
                .orElse(new RiskWarning());

        warning.setDisputeId(disputeId);
        warning.setRiskLevel(riskLevel);
        warning.setDescription(description);

        return riskWarningRepository.save(warning);
    }

    @Transactional
    public RiskWarning markIntervened(Long id, String result) {
        RiskWarning warning = riskWarningRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("风险预警不存在"));

        warning.setIntervened(true);
        warning.setInterventionResult(result);

        return riskWarningRepository.save(warning);
    }

    public List<RiskWarning> findByRiskLevel(RiskLevel riskLevel) {
        return riskWarningRepository.findByRiskLevel(riskLevel);
    }

    public Optional<RiskWarning> findByDisputeId(Long disputeId) {
        return riskWarningRepository.findByDisputeId(disputeId);
    }

    public Optional<RiskWarning> findById(Long id) {
        return riskWarningRepository.findById(id);
    }
}
