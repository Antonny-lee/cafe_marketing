package com.cafe.dashboard.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "fixed_costs")
@Getter
@Setter
public class FixedCost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "store_id", length = 10, nullable = false)
    private String storeId;

    @Column(name = "category", length = 50, nullable = false)
    private String category;

    @Column(name = "vendor", length = 200)
    private String vendor;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "payment_method", length = 20)
    private String paymentMethod;

    @Column(name = "day_of_month", nullable = false)
    private Integer dayOfMonth;

    @Column(name = "memo", length = 500)
    private String memo;

    @Column(name = "active", length = 1)
    private String active;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
