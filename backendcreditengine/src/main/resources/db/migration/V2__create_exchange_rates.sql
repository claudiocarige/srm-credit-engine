CREATE TABLE exchange_rates (
    id UUID NOT NULL,
    currency VARCHAR(3) NOT NULL,
    rate_brl NUMERIC(19, 6) NOT NULL,
    effective_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_exchange_rates PRIMARY KEY (id),
    CONSTRAINT uk_exchange_rates_currency_effective_at UNIQUE (currency, effective_at),
    CONSTRAINT ck_exchange_rates_currency CHECK (currency = 'USD'),
    CONSTRAINT ck_exchange_rates_rate_brl_range CHECK (rate_brl BETWEEN 0.000001 AND 9999999.999999)
);

