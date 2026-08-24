package com.cafe.dashboard.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "market_report_series")
@Getter
@Setter
public class MarketReportSeries {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id")
    private Long reportId;

    @Column(name = "metric_name", length = 50)
    private String metricName;

    @Column(name = "quarter_label", length = 20)
    private String quarterLabel;

    @Column(name = "mine_value")
    private Double mineValue;

    @Column(name = "gu_value")
    private Double guValue;

    @Column(name = "seoul_value")
    private Double seoulValue;
}
