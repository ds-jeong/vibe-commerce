package com.example.ecommerce.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QDailySettlementSummary is a Querydsl query type for DailySettlementSummary
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QDailySettlementSummary extends EntityPathBase<DailySettlementSummary> {

    private static final long serialVersionUID = -2106148977L;

    public static final QDailySettlementSummary dailySettlementSummary = new QDailySettlementSummary("dailySettlementSummary");

    public final NumberPath<java.math.BigDecimal> dailyNetSettlement = createNumber("dailyNetSettlement", java.math.BigDecimal.class);

    public final NumberPath<java.math.BigDecimal> dailyTotalPgFee = createNumber("dailyTotalPgFee", java.math.BigDecimal.class);

    public final NumberPath<java.math.BigDecimal> dailyTotalPlatformFee = createNumber("dailyTotalPlatformFee", java.math.BigDecimal.class);

    public final NumberPath<java.math.BigDecimal> dailyTotalSales = createNumber("dailyTotalSales", java.math.BigDecimal.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final DatePath<java.time.LocalDate> summaryDate = createDate("summaryDate", java.time.LocalDate.class);

    public final NumberPath<Long> totalOrderCount = createNumber("totalOrderCount", Long.class);

    public QDailySettlementSummary(String variable) {
        super(DailySettlementSummary.class, forVariable(variable));
    }

    public QDailySettlementSummary(Path<? extends DailySettlementSummary> path) {
        super(path.getType(), path.getMetadata());
    }

    public QDailySettlementSummary(PathMetadata metadata) {
        super(DailySettlementSummary.class, metadata);
    }

}

