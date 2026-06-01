package com.af.novadesk.api.finance.mapper;

import com.af.novadesk.api.common.service.FileStorageService;
import com.af.novadesk.api.finance.dto.ExpenseAttachmentDto;
import com.af.novadesk.api.finance.entity.ExpenseAttachment;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;

/**
 * MapStruct mapper for {@link ExpenseAttachment} → {@link ExpenseAttachmentDto}.
 *
 * <p>Implemented as an abstract class (rather than an interface) so that
 * {@link FileStorageService} can be injected via {@code @Autowired} into the
 * generated Spring component. MapStruct generates a concrete subclass annotated
 * with {@code @Component}; Spring then satisfies the {@code fileStorageService}
 * field when it wires the bean.</p>
 *
 * <p>The {@code @AfterMapping} method runs after all structural field mappings
 * are complete and generates a 15-minute pre-signed download URL for each
 * attachment using the stored {@code storageKey}.</p>
 */
@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public abstract class ExpenseAttachmentMapper {

    /** Pre-signed URL validity window (LLR-FIN-03.4: expires after 15 minutes). */
    private static final Duration PRESIGN_EXPIRY = Duration.ofMinutes(15);

    @Autowired
    protected FileStorageService fileStorageService;

    @Mapping(target = "expenseTransactionId", source = "expenseTransaction.id")
    @Mapping(target = "uploadedByUserId",     source = "uploadedBy.id")
    @Mapping(target = "downloadUrl",          ignore = true)
    public abstract ExpenseAttachmentDto toDto(ExpenseAttachment entity);

    /**
     * Runs after all structural field mappings are set.
     * Generates a short-lived pre-signed URL and populates {@code downloadUrl}.
     */
    @AfterMapping
    protected void fillDownloadUrl(final ExpenseAttachment entity,
                                   @MappingTarget final ExpenseAttachmentDto dto) {
        if (entity.getStorageKey() != null) {
            dto.setDownloadUrl(
                    fileStorageService.generatePresignedUrl(entity.getStorageKey(), PRESIGN_EXPIRY)
            );
        }
    }

    public abstract List<ExpenseAttachmentDto> toDtoList(List<ExpenseAttachment> entities);
}
