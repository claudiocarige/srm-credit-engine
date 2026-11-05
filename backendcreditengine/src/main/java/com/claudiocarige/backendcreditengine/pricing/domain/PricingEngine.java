package com.claudiocarige.backendcreditengine.pricing.domain;

import com.claudiocarige.backendcreditengine.pricing.domain.calculation.CompoundInterestCalculator;
import com.claudiocarige.backendcreditengine.pricing.domain.model.PricingInput;
import com.claudiocarige.backendcreditengine.pricing.domain.model.PricingResult;
import com.claudiocarige.backendcreditengine.pricing.domain.model.ReceivableType;
import com.claudiocarige.backendcreditengine.pricing.domain.strategy.PostDatedCheckPricingStrategy;
import com.claudiocarige.backendcreditengine.pricing.domain.strategy.PricingStrategy;
import com.claudiocarige.backendcreditengine.pricing.domain.strategy.TradeBillPricingStrategy;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Ponto de entrada do domínio financeiro para precificação de recebíveis em BRL.
 * <p>
 * Respeita o Open/Closed Principle através de injeção de dependências das estratégias
 * no construtor Java puro. Valida defensivamente os limites financeiros aprovados
 * no contrato da API antes de delegar a execução à estratégia correspondente.
 */
public final class PricingEngine {

    public static final BigDecimal MIN_FACE_VALUE = new BigDecimal("0.01");
    public static final BigDecimal MAX_FACE_VALUE = new BigDecimal("999999999.99");
    public static final int MAX_FACE_VALUE_SCALE = 2;
    public static final int MAX_BASE_RATE_SCALE = 6;
    public static final int MIN_TERM_MONTHS = 1;

    private final Map<ReceivableType, PricingStrategy> strategies;

    /**
     * Construtor padrão que inicializa o motor com as estratégias padrão do sistema.
     */
    public PricingEngine() {
         this(createDefaultStrategies());
    }

    private static List<PricingStrategy> createDefaultStrategies() {

        CompoundInterestCalculator calculator =
                new CompoundInterestCalculator();

        return List.of(
                new TradeBillPricingStrategy(calculator),
                new PostDatedCheckPricingStrategy(calculator)
        );
    }
    /**
     * Construtor por injeção de dependências via Java puro (Strategy Pattern + OCP).
     *
     * @param strategyList lista de estratégias disponíveis
     * @throws PricingValidationException se a lista for nula/vazia, contiver nulos ou tipos duplicados
     */
    public PricingEngine(List<PricingStrategy> strategyList) {
        if (strategyList == null || strategyList.isEmpty()) {
            throw new PricingValidationException("Strategy list must not be null or empty");
        }

        Map<ReceivableType, PricingStrategy> map = new EnumMap<>(ReceivableType.class);
        for (PricingStrategy strategy : strategyList) {
            if (strategy == null) {
                throw new PricingValidationException("PricingStrategy element must not be null");
            }
            ReceivableType type = strategy.getSupportedType();
            if (type == null) {
                throw new PricingValidationException("Strategy supported type must not be null");
            }
            if (map.containsKey(type)) {
                throw new PricingValidationException("Duplicate strategy registered for type: " + type);
            }
            map.put(type, strategy);
        }
        this.strategies = Collections.unmodifiableMap(map);
    }

    /**
     * Precifica o recebível conforme o tipo e parâmetros informados.
     *
     * @param input dados de entrada imutáveis
     * @return resultado detalhado da precificação
     * @throws PricingValidationException se algum limite financeiro for violado ou não houver strategy registrada
     */
    public PricingResult calculate(PricingInput input) {
        validateInput(input);

        PricingStrategy strategy = strategies.get(input.type());
        if (strategy == null) {
            throw new PricingValidationException("No pricing strategy registered for type: " + input.type());
        }

        return strategy.calculate(input);
    }

    private void validateInput(PricingInput input) {
        if (input == null) {
            throw new PricingValidationException("PricingInput must not be null");
        }
        if (input.type() == null) {
            throw new PricingValidationException("ReceivableType must not be null");
        }

        // Validação de FaceValueBrl
        BigDecimal faceValue = input.faceValueBrl();
        if (faceValue == null) {
            throw new PricingValidationException("Face value must not be null");
        }
        if (faceValue.scale() > MAX_FACE_VALUE_SCALE) {
            throw new PricingValidationException("Face value scale must not exceed 2 decimal places");
        }
        if (faceValue.compareTo(MIN_FACE_VALUE) < 0 || faceValue.compareTo(MAX_FACE_VALUE) > 0) {
            throw new PricingValidationException(
                "Face value must be between " + MIN_FACE_VALUE + " and " + MAX_FACE_VALUE
            );
        }

        // Validação de TermMonths
        if (input.termMonths() < MIN_TERM_MONTHS) {
            throw new PricingValidationException("Term months must be at least " + MIN_TERM_MONTHS);
        }

        // Validação de BaseRateMonthly
        BigDecimal baseRate = input.baseRateMonthly();
        if (baseRate == null) {
            throw new PricingValidationException("Base rate monthly must not be null");
        }
        if (baseRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new PricingValidationException("Base rate monthly must be non-negative");
        }
        if (baseRate.scale() > MAX_BASE_RATE_SCALE) {
            throw new PricingValidationException(
                "Base rate monthly scale must not exceed " + MAX_BASE_RATE_SCALE + " decimal places"
            );
        }
    }
}
