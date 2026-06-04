package com.af.novadesk.api.asset.constants;

public enum DepreciationMethod {
    /** Annual depreciation = purchase_cost / useful_life_years */
    STRAIGHT_LINE,
    /** Annual depreciation = net_book_value * depreciation_rate */
    DECLINING_BALANCE
}
