package com.af.novadesk.api.expense.dto;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.expense.constants.ExpenseTransactionStatus;
import com.af.novadesk.api.expense.constants.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Unified DTO for an {@link com.af.novadesk.api.expense.entity.ExpenseTransaction}.
 *
 * <p>Used for both the create request body and the response payload.
 * Server-assigned fields are ignored on inbound requests and populated on responses.</p>
 *
 * <p>The system enforces that {@code sourceAccountId ≠ destinationAccountId}
 * at the service layer before posting ledger entries (LLR-FIN-03.2).</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Expense transaction data transfer object (LLR-FIN-03)")
public class ExpenseTransactionDto {

    /* ── server-assigned ─────────────────────────────────────────── */

    @Schema(description = "Transaction UUID — assigned by the server on creation", accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id;

    @Schema(description = "Accounting lifecycle state: POSTED (default) or VOID", accessMode = Schema.AccessMode.READ_ONLY)
    @JsonProperty("transaction_status")
    private ExpenseTransactionStatus transactionStatus;

    @Schema(description = "Soft-delete state of the record", accessMode = Schema.AccessMode.READ_ONLY)
    private Status status;

    @Schema(description = "ISO 4217 currency code — derived from the legal entity's base currency at submission time",
            accessMode = Schema.AccessMode.READ_ONLY, example = "USD")
    @JsonProperty("currency_code")
    private String currencyCode;

    @Schema(description = "Shadow user ID of the finance operator who recorded this expense",
            accessMode = Schema.AccessMode.READ_ONLY)
    @JsonProperty("created_by_user_id")
    private UUID createdByUserId;

    @Schema(description = "Attachments linked to this transaction", accessMode = Schema.AccessMode.READ_ONLY)
    private List<ExpenseAttachmentDto> attachments;

    @Schema(description = "Timestamp when the transaction was recorded", accessMode = Schema.AccessMode.READ_ONLY)
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp of the last update", accessMode = Schema.AccessMode.READ_ONLY)
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    /* ── client-supplied ─────────────────────────────────────────── */

    @NotNull(message = "Legal entity ID is required")
    @Schema(description = "UUID of the legal entity this expense is recorded against",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    @JsonProperty("legal_entity_id")
    private UUID legalEntityId;

    @NotNull(message = "Vendor is required")
    @Schema(description = "UUID of the vendor (payee) this expense was paid to",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    @JsonProperty("vendor_id")
    private UUID vendorId;

    @NotNull(message = "Expense date is required")
    @PastOrPresent(message = "Expense date cannot be in the future")
    @Schema(description = "Date the expense was incurred", example = "2026-05-22")
    @JsonProperty("expense_date")
    private LocalDate expenseDate;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @DecimalMax(value = "999999999999999.9999", message = "Amount exceeds maximum allowed value")
    @Digits(integer = 15, fraction = 4, message = "Amount must have at most 15 integer digits and 4 decimal places")
    @Schema(description = "Transaction amount in the entity's base currency", example = "500.00")
    private BigDecimal amount;

    @NotNull(message = "Payment method is required")
    @Schema(description = "How the expense was settled",
            example = "BANK_TRANSFER",
            allowableValues = {"BANK_TRANSFER", "CREDIT_CARD", "CASH", "CHECK"})
    @JsonProperty("payment_method")
    private PaymentMethod paymentMethod;

    @NotNull(message = "Source account is required")
    @Schema(description = "UUID of the account being CREDITED — funds leave this account (e.g. Company Bank Account)",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    @JsonProperty("source_account_id")
    private UUID sourceAccountId;

    @NotNull(message = "Destination account is required")
    @Schema(description = "UUID of the account being DEBITED — expense is recognised here (e.g. Cloud Infrastructure Expense)",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    @JsonProperty("destination_account_id")
    private UUID destinationAccountId;

    @Size(max = 50, message = "Invoice/receipt number must not exceed 50 characters")
    @Schema(description = "Vendor-issued invoice or receipt number for reconciliation (optional)", example = "INV-2026-00123")
    @JsonProperty("invoice_receipt_number")
    private String invoiceReceiptNumber;

    @NotBlank(message = "Description is required")
    @Size(max = 500, message = "Description must not exceed 500 characters")
    @Schema(description = "What the expense was for — appears in the general ledger", example = "Monthly server bill - May 2026")
    private String description;
}
