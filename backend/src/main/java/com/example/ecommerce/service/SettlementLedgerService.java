package com.example.ecommerce.service;

import com.example.ecommerce.domain.Orders;
import com.example.ecommerce.domain.Settlement;
import com.example.ecommerce.repository.SettlementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SettlementLedgerService {

    public static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("50000");
    public static final BigDecimal DEFAULT_DELIVERY_FEE = new BigDecimal("3000");
    public static final BigDecimal PG_RATE = new BigDecimal("0.033");
    public static final BigDecimal PLATFORM_RATE = new BigDecimal("0.10");
    private static final int SCALE = 2;

    private final SettlementRepository settlementRepository;

    public SettlementLedgerService(SettlementRepository settlementRepository) {
        this.settlementRepository = settlementRepository;
    }

    public Map<String, BigDecimal> computeLedger(BigDecimal goodsAmount) {
        BigDecimal goods = nvl(goodsAmount);
        BigDecimal deliveryFee = BigDecimal.ZERO;
        if (goods.compareTo(BigDecimal.ZERO) > 0 && goods.compareTo(FREE_SHIPPING_THRESHOLD) < 0) {
            deliveryFee = DEFAULT_DELIVERY_FEE;
        }
        BigDecimal totalAmount = goods.add(deliveryFee);
        BigDecimal pgFee = totalAmount.multiply(PG_RATE).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal platformFee = goods.multiply(PLATFORM_RATE).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal partnerSettlementAmount = totalAmount.subtract(pgFee).subtract(platformFee)
                .setScale(SCALE, RoundingMode.HALF_UP);

        Map<String, BigDecimal> ledger = new LinkedHashMap<>();
        ledger.put("goodsAmount", goods);
        ledger.put("deliveryFee", deliveryFee);
        ledger.put("totalAmount", totalAmount);
        ledger.put("pgFee", pgFee);
        ledger.put("platformFee", platformFee);
        ledger.put("partnerSettlementAmount", partnerSettlementAmount);
        return ledger;
    }

    public void applyCheckoutLedger(Orders order, BigDecimal goodsAmount) {
        if (order == null) {
            return;
        }
        Map<String, BigDecimal> ledger = computeLedger(goodsAmount);
        order.setDeliveryFee(ledger.get("deliveryFee"));
        order.setTotalAmount(ledger.get("totalAmount"));
        order.setNetAmount(ledger.get("totalAmount"));
        order.setPgFee(ledger.get("pgFee"));
        order.setPlatformFee(ledger.get("platformFee"));
        order.setPartnerSettlementAmount(ledger.get("partnerSettlementAmount"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPaidSettlement(Orders order) {
        if (order == null || order.getId() == null) {
            return;
        }
        fillMissingFeeColumnsWithoutChangingPayable(order);
        Settlement settlement = settlementRepository.findByOrder_Id(order.getId()).orElseGet(Settlement::new);
        settlement.setOrder(order);
        settlement.setTotalSalesAmount(nvl(order.getTotalAmount()));
        settlement.setPgFee(nvl(order.getPgFee()));
        settlement.setPlatformFee(nvl(order.getPlatformFee()));
        settlement.setNetSettlementAmount(nvl(order.getPartnerSettlementAmount()));
        if (settlement.getSettledAt() == null) {
            settlement.setSettledAt(LocalDateTime.now());
        }
        settlementRepository.save(settlement);
    }

    private void fillMissingFeeColumnsWithoutChangingPayable(Orders order) {
        BigDecimal total = order.getTotalAmount() != null ? order.getTotalAmount() : nvl(order.getNetAmount());
        BigDecimal delivery = nvl(order.getDeliveryFee());
        BigDecimal goods = total.subtract(delivery);
        if (goods.compareTo(BigDecimal.ZERO) < 0) {
            goods = BigDecimal.ZERO;
        }
        if (order.getDeliveryFee() == null) {
            order.setDeliveryFee(delivery);
        }
        if (order.getPgFee() == null) {
            order.setPgFee(total.multiply(PG_RATE).setScale(SCALE, RoundingMode.HALF_UP));
        }
        if (order.getPlatformFee() == null) {
            order.setPlatformFee(goods.multiply(PLATFORM_RATE).setScale(SCALE, RoundingMode.HALF_UP));
        }
        if (order.getPartnerSettlementAmount() == null) {
            order.setPartnerSettlementAmount(
                    total.subtract(nvl(order.getPgFee())).subtract(nvl(order.getPlatformFee()))
                            .setScale(SCALE, RoundingMode.HALF_UP)
            );
        }
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
