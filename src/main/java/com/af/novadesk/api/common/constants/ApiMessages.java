package com.af.novadesk.api.common.constants;
/**
 * Standard API response messages for all endpoints.
 */
public final class ApiMessages {
    private ApiMessages() {
        throw new AssertionError("Utility class should not be instantiated");
    }
    public static final String RECORD_CREATED_SUCCESS = "Record created successfully";
    public static final String RECORD_UPDATED_SUCCESS = "Record updated successfully";
    public static final String RECORD_DELETED_SUCCESS = "Record deleted successfully";
    public static final String RECORDS_RETRIEVED_SUCCESS = "Records retrieved successfully";
    public static final String RECORD_RETRIEVED_SUCCESS = "Record retrieved successfully";
    public static final String OPERATION_SUCCESS = "Operation completed successfully";
    public static final String CAPITAL_INJECTION_SUCCESS = "Capital injection recorded successfully";
    public static final String CAPITAL_INJECTION_CREATED = "Capital injection created and posted to ledger";
    public static final String JOURNAL_ENTRY_SUCCESS = "Journal entry created successfully";
    public static final String LEDGER_POSTING_SUCCESS = "Ledger posting completed successfully";
    public static final String EXCHANGE_RATE_APPLIED = "Exchange rate applied successfully";
    public static final String BALANCE_VERIFIED = "Account balance verified";
    public static final String VALIDATION_FAILED = "Validation failed. Please check the errors and try again";
    public static final String INVALID_INPUT = "Invalid input provided";
    public static final String INVALID_AMOUNT = "Amount must be greater than zero";
    public static final String INVALID_CURRENCY = "Invalid currency code. Must be a valid ISO 4217 code";
    public static final String INVALID_DATE = "Invalid date provided";
    public static final String INVALID_ENTITY_CODE = "Invalid entity code";
    public static final String INVALID_ACCOUNT = "Invalid account or account number";
    public static final String MISSING_REQUIRED_FIELD = "Required field is missing";
    public static final String INVALID_EXCHANGE_RATE = "Exchange rate must be positive";
    public static final String BAD_REQUEST = "Bad request. Please verify your input";
    public static final String NOT_FOUND = "Requested resource not found";
    public static final String ENTITY_NOT_FOUND = "Entity not found";
    public static final String ACCOUNT_NOT_FOUND = "Account not found";
    public static final String RECORD_NOT_FOUND = "Record not found";
    public static final String DUPLICATE_RECORD = "This record already exists";
    public static final String UNBALANCED_ENTRIES = "Journal entries are not balanced. Total debits must equal total credits";
    public static final String MISSING_EXCHANGE_RATE = "Exchange rate data is unavailable. Please provide a manual exchange rate";
    public static final String CONFLICT = "Operation conflicts with existing data or business rules";
    public static final String RESOURCE_FORBIDDEN = "You do not have permission to access this resource";
    public static final String INTERNAL_SERVER_ERROR = "An internal error occurred while processing your request. Please try again later";
    public static final String SERVICE_UNAVAILABLE = "Service is temporarily unavailable. Please try again later";
    public static final String DATABASE_ERROR = "Database error occurred. Please contact support if the problem persists";
    public static final String EXTERNAL_SERVICE_ERROR = "External service error. Please try again later";
    public static final String PROCESSING_REQUEST = "Processing your request";
    public static final String AWAITING_APPROVAL = "Record created and awaiting approval";
    public static final String COMPLETED = "Completed";
    public static final String IN_PROGRESS = "In progress";
    public static final String FAILED = "Failed";
    public static final String PENDING = "Pending";
    public static final String CANCELLED = "Cancelled";
    public static final String INSUFFICIENT_FUNDS = "Insufficient funds in the source account";
    public static final String ENTITY_MISMATCH = "Source and target entities do not match expected configuration";
    public static final String ACCOUNT_LOCKED = "Account is locked and cannot be modified";
    public static final String TRANSACTION_LIMIT_EXCEEDED = "Transaction amount exceeds allowed limit";
    public static final String SAME_ENTITY_TRANSFER = "Cannot transfer between the same entity";
    public static final String JOURNAL_MISMATCH = "Journal entries do not match";
}