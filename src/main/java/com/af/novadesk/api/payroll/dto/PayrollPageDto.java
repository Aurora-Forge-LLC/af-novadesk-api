package com.af.novadesk.api.payroll.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Generic pagination wrapper for payroll list responses.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PayrollPageDto<T> {

    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
