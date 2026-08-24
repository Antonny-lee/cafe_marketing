package com.cafe.dashboard.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_sales")
@IdClass(DailySalesId.class)
@Getter
@Setter
public class DailySales {

    @Id
    @Column(name = "store_id", length = 10)
    private String storeId;

    @Id
    @Column(name = "sale_date")
    private LocalDate saleDate;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "source", length = 20)
    private String source;

    @Column(name = "uploaded_file", length = 300)
    private String uploadedFile;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
