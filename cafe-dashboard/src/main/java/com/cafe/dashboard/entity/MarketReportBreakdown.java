package com.cafe.dashboard.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "market_report_breakdown")
@Getter
@Setter
public class MarketReportBreakdown {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id")
    private Long reportId;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "label", length = 50)
    private String label;

    @Column(name = "value")
    private Double value;
}
