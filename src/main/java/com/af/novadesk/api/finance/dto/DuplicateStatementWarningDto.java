package com.af.novadesk.api.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Warning response returned when an upload request matches an existing statement
 * for the same bank account + period.
 *
 * <p>The frontend should present the user with a choice:
 * <ul>
 *   <li><b>REPLACE</b> — supersede the existing statement and upload the new one</li>
 *   <li><b>CANCEL</b> — abort the upload</li>
 * </ul>
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DuplicateStatementWarningDto {

    private UUID existingStatementId;
    private LocalDateTime uploadedDate;
    private String uploadedByDisplayName;
    private String originalFilename;
    private UUID bankAccountId;
}
