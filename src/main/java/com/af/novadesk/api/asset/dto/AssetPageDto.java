package com.af.novadesk.api.asset.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class AssetPageDto {
    private List<AssetDto> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
