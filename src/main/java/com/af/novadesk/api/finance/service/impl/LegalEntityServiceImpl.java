package com.af.novadesk.api.finance.service.impl;
import com.af.novadesk.api.finance.entity.LegalEntity;
import com.af.novadesk.api.finance.dto.LegalEntitySummaryResponse;
import com.af.novadesk.api.finance.exception.NotFoundException;
import com.af.novadesk.api.finance.repository.LegalEntityRepository;
import com.af.novadesk.api.finance.service.LegalEntityService;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;
@Service
public class LegalEntityServiceImpl implements LegalEntityService {
    private final LegalEntityRepository legalEntityRepository;
    public LegalEntityServiceImpl(LegalEntityRepository legalEntityRepository) {
        this.legalEntityRepository = legalEntityRepository;
    }
    @Override
    public List<LegalEntitySummaryResponse> list() {
        return legalEntityRepository.findAll().stream().map(this::toSummary).toList();
    }
    @Override
    public LegalEntitySummaryResponse getById(UUID id) {
        LegalEntity entity = legalEntityRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Legal entity not found with id: " + id));
        return toSummary(entity);
    }
    private LegalEntitySummaryResponse toSummary(LegalEntity entity) {
        return new LegalEntitySummaryResponse(
                entity.getId(),
                entity.getEntityName(),
                entity.getEntityCode(),
                entity.getCountry(),
                entity.getBaseCurrency(),
                entity.getApprovalStatus(),
                entity.getStatus(),
                entity.getIncorporationDate(),
                entity.getCreatedAt()
        );
    }
}
