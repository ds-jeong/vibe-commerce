package com.example.ecommerce.global;

import com.example.ecommerce.domain.InquiryStatus;
import com.example.ecommerce.domain.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

@Component
public class SchemaCheckConstraintMigrator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaCheckConstraintMigrator.class);

    private final JdbcTemplate jdbcTemplate;

    public SchemaCheckConstraintMigrator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        replaceCheckConstraint(
                "orders",
                "orders_status_check",
                "status",
                Arrays.stream(OrderStatus.values()).map(Enum::name).toArray(String[]::new)
        );
        replaceCheckConstraint(
                "inquiries",
                "inquiries_status_check",
                "status",
                Arrays.stream(InquiryStatus.values()).map(Enum::name).toArray(String[]::new)
        );
    }

    private void replaceCheckConstraint(String table, String constraint, String column, String[] allowed) {
        String inList = Arrays.stream(allowed)
                .map(value -> "'" + value.replace("'", "''") + "'")
                .collect(Collectors.joining(", "));
        jdbcTemplate.execute("ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + constraint);
        jdbcTemplate.execute(
                "ALTER TABLE " + table
                        + " ADD CONSTRAINT " + constraint
                        + " CHECK ((" + column + ")::text = ANY ((ARRAY[" + inList + "]::character varying[])::text[]))"
        );
        log.info("Updated {} to allow values: {}", constraint, String.join(", ", allowed));
    }
}
