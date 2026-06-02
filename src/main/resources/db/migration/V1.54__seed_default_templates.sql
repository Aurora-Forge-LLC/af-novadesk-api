-- =============================================================================
-- NOVADESK API - Seed Default Template Data
-- Version  : 1.54
-- Created  : 2026-06-02
-- Purpose  : Populate all four template tables with default data for US, IN,
--            and NP countries.
--
-- Tables   : af_novadesk.coa_templates
--            af_novadesk.bank_account_templates
--            af_novadesk.fa_account_templates
--            af_novadesk.fiscal_year_templates
-- =============================================================================

SET search_path TO af_novadesk;

-- ===========================================================================
-- Chart of Accounts Templates
-- ===========================================================================
INSERT INTO af_novadesk.coa_templates
    (country_code, account_code, account_name, account_type, is_postable, sort_order)
VALUES
    -- US (GAAP-aligned)
    ('US', '1000', 'Cash and Cash Equivalents',             'ASSET',    true,  1),
    ('US', '1100', 'Accounts Receivable',                    'ASSET',    true,  2),
    ('US', '1200', 'Inventory',                              'ASSET',    true,  3),
    ('US', '1300', 'Prepaid Expenses',                       'ASSET',    true,  4),
    ('US', '1400', 'Fixed Assets',                           'ASSET',    false, 5),
    ('US', '1410', 'Property, Plant & Equipment',            'ASSET',    true,  6),
    ('US', '1500', 'Accumulated Depreciation',               'ASSET',    true,  7),
    ('US', '2000', 'Accounts Payable',                       'LIABILITY',true,  8),
    ('US', '2100', 'Accrued Liabilities',                    'LIABILITY',true,  9),
    ('US', '2200', 'Short-term Borrowings',                  'LIABILITY',true, 10),
    ('US', '2300', 'Long-term Debt',                         'LIABILITY',true, 11),
    ('US', '3000', 'Common Stock',                           'EQUITY',   true, 12),
    ('US', '3100', 'Retained Earnings',                      'EQUITY',   true, 13),
    ('US', '4000', 'Revenue',                                'REVENUE',  true, 14),
    ('US', '4100', 'Service Revenue',                        'REVENUE',  true, 15),
    ('US', '5000', 'Cost of Goods Sold',                     'EXPENSE',  true, 16),
    ('US', '5100', 'Salaries & Wages',                       'EXPENSE',  true, 17),
    ('US', '5200', 'Rent Expense',                           'EXPENSE',  true, 18),
    ('US', '5300', 'Utilities Expense',                      'EXPENSE',  true, 19),
    ('US', '5400', 'Depreciation Expense',                   'EXPENSE',  true, 20),
    ('US', '5500', 'Tax Expense',                            'EXPENSE',  true, 21),
    -- India (Ind AS / Schedule III)
    ('IN', '1000', 'Cash and Bank Balances',                 'ASSET',    true,  1),
    ('IN', '1100', 'Trade Receivables',                      'ASSET',    true,  2),
    ('IN', '1200', 'Inventories',                            'ASSET',    true,  3),
    ('IN', '1300', 'Loans & Advances',                       'ASSET',    true,  4),
    ('IN', '1400', 'Fixed Assets (Tangible)',                'ASSET',    false, 5),
    ('IN', '1410', 'Property, Plant & Equipment',            'ASSET',    true,  6),
    ('IN', '1500', 'Intangible Assets',                      'ASSET',    true,  7),
    ('IN', '2000', 'Trade Payables',                         'LIABILITY',true,  8),
    ('IN', '2100', 'Other Current Liabilities',              'LIABILITY',true,  9),
    ('IN', '2200', 'Short-term Borrowings',                  'LIABILITY',true, 10),
    ('IN', '2300', 'Long-term Borrowings',                   'LIABILITY',true, 11),
    ('IN', '3000', 'Share Capital',                          'EQUITY',   true, 12),
    ('IN', '3100', 'Reserves & Surplus',                     'EQUITY',   true, 13),
    ('IN', '4000', 'Revenue from Operations',                'REVENUE',  true, 14),
    ('IN', '4100', 'Other Income',                           'REVENUE',  true, 15),
    ('IN', '5000', 'Cost of Materials Consumed',             'EXPENSE',  true, 16),
    ('IN', '5100', 'Employee Benefits Expense',              'EXPENSE',  true, 17),
    ('IN', '5200', 'Finance Costs',                          'EXPENSE',  true, 18),
    ('IN', '5300', 'Depreciation & Amortisation',            'EXPENSE',  true, 19),
    ('IN', '5400', 'Tax Expense',                            'EXPENSE',  true, 20),
    -- Nepal (NFRS)
    ('NP', '1000', 'Cash and Cash Equivalents',              'ASSET',    true,  1),
    ('NP', '1100', 'Accounts Receivable',                    'ASSET',    true,  2),
    ('NP', '1200', 'Inventory',                              'ASSET',    true,  3),
    ('NP', '1300', 'Prepayments',                            'ASSET',    true,  4),
    ('NP', '1400', 'Property, Plant & Equipment',            'ASSET',    false, 5),
    ('NP', '1410', 'Land & Building',                        'ASSET',    true,  6),
    ('NP', '1420', 'Furniture & Fixtures',                   'ASSET',    true,  7),
    ('NP', '1500', 'Intangible Assets',                      'ASSET',    true,  8),
    ('NP', '2000', 'Accounts Payable',                       'LIABILITY',true,  9),
    ('NP', '2100', 'Accrued Expenses',                       'LIABILITY',true, 10),
    ('NP', '2200', 'Short-term Loans',                       'LIABILITY',true, 11),
    ('NP', '2300', 'Long-term Loans',                        'LIABILITY',true, 12),
    ('NP', '2400', 'Shareholder Loans',                      'LIABILITY',true, 13),
    ('NP', '3000', 'Share Capital',                          'EQUITY',   true, 14),
    ('NP', '3100', 'Retained Earnings',                      'EQUITY',   true, 15),
    ('NP', '4000', 'Revenue',                                'REVENUE',  true, 16),
    ('NP', '5000', 'Cost of Sales',                          'EXPENSE',  true, 17),
    ('NP', '5100', 'Administrative Expenses',                'EXPENSE',  true, 18),
    ('NP', '5200', 'Selling & Distribution Expenses',        'EXPENSE',  true, 19),
    ('NP', '5300', 'Finance Costs',                          'EXPENSE',  true, 20),
    ('NP', '5400', 'Depreciation',                           'EXPENSE',  true, 21),
    ('NP', '5500', 'Tax Expense',                            'EXPENSE',  true, 22);

-- ===========================================================================
-- Bank Account Templates
-- ===========================================================================
INSERT INTO af_novadesk.bank_account_templates
    (country_code, account_type, account_label, sort_order)
VALUES
    ('US', 'CASH',      'Petty Cash – US',              1),
    ('US', 'OPERATING', 'Main Operating Account – US',   2),
    ('US', 'SAVINGS',   'Savings Account – US',          3),
    ('IN', 'CASH',      'Cash in Hand – IN',             1),
    ('IN', 'OPERATING', 'Current Account – IN',          2),
    ('IN', 'SAVINGS',   'Savings Account – IN',          3),
    ('NP', 'CASH',      'Petty Cash – NP',               1),
    ('NP', 'OPERATING', 'Operating Account – NP',        2),
    ('NP', 'SAVINGS',   'Savings Account – NP',          3);

-- ===========================================================================
-- FA (Funding) Account Templates
-- ===========================================================================
INSERT INTO af_novadesk.fa_account_templates
    (country_code, account_code, account_name, account_role, account_type, sort_order)
VALUES
    ('US', '1000', 'Bank - Operating Account (USD)',      'BANK_OPERATING',          'ASSET',    1),
    ('US', '1100', 'Petty Cash (USD)',                     'CASH',                    'ASSET',    2),
    ('US', '1200', 'Inter-Entity Receivable (USD)',        'INTER_ENTITY_RECEIVABLE', 'ASSET',    3),
    ('US', '3000', 'Founders - Equity (USD)',              'FOUNDER_EQUITY',          'EQUITY',   4),
    ('US', '2100', 'Loan Payable (USD)',                   'LOAN_PAYABLE',            'LIABILITY',5),
    ('US', '2500', 'Inter-Entity Payable (USD)',           'INTER_ENTITY_PAYABLE',    'LIABILITY',6),
    ('US', '4000', 'Grant Income (USD)',                   'GRANT_INCOME',            'REVENUE',  7),
    ('IN', '1000', 'Bank - Operating Account (INR)',      'BANK_OPERATING',          'ASSET',    1),
    ('IN', '1100', 'Cash in Hand (INR)',                   'CASH',                    'ASSET',    2),
    ('IN', '1200', 'Inter-Entity Receivable (INR)',        'INTER_ENTITY_RECEIVABLE', 'ASSET',    3),
    ('IN', '3000', 'Founders - Equity (INR)',              'FOUNDER_EQUITY',          'EQUITY',   4),
    ('IN', '2100', 'Loan Payable (INR)',                   'LOAN_PAYABLE',            'LIABILITY',5),
    ('IN', '2500', 'Inter-Entity Payable (INR)',           'INTER_ENTITY_PAYABLE',    'LIABILITY',6),
    ('IN', '4000', 'Grant Income (INR)',                   'GRANT_INCOME',            'REVENUE',  7),
    ('NP', '1000', 'Bank - Operating Account (NPR)',      'BANK_OPERATING',          'ASSET',    1),
    ('NP', '1100', 'Petty Cash (NPR)',                     'CASH',                    'ASSET',    2),
    ('NP', '1200', 'Inter-Entity Receivable (NPR)',        'INTER_ENTITY_RECEIVABLE', 'ASSET',    3),
    ('NP', '3000', 'Founders - Equity (NPR)',              'FOUNDER_EQUITY',          'EQUITY',   4),
    ('NP', '2100', 'Loan Payable (NPR)',                   'LOAN_PAYABLE',            'LIABILITY',5),
    ('NP', '2500', 'Inter-Entity Payable (NPR)',           'INTER_ENTITY_PAYABLE',    'LIABILITY',6),
    ('NP', '4000', 'Grant Income (NPR)',                   'GRANT_INCOME',            'REVENUE',  7);

-- ===========================================================================
-- Fiscal Year Templates
-- ===========================================================================
INSERT INTO af_novadesk.fiscal_year_templates
    (country_code, fiscal_start_month, fiscal_start_day, fiscal_end_month, fiscal_end_day, periods_per_year)
VALUES
    ('US', 1,  1, 12, 31, 12),   -- Jan 1 -> Dec 31 (calendar year)
    ('IN', 4,  1,  3, 31, 12),   -- Apr 1 -> Mar 31 (Indian fiscal year)
    ('NP', 7, 16,  7, 15, 12);   -- Jul 16 -> Jul 15 (Bikram Sambat)
