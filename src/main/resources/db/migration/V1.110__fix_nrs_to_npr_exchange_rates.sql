-- Correct non-ISO currency code 'NRS' to the ISO 4217 code 'NPR' (Nepalese Rupee)
-- that was incorrectly entered through the exchange rate creation form.
SET search_path TO af_novadesk;

UPDATE fa_exchange_rates
SET source_currency = 'NPR'
WHERE source_currency = 'NRS';

UPDATE fa_exchange_rates
SET target_currency = 'NPR'
WHERE target_currency = 'NRS';
