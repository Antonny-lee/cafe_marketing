package com.cafe.dashboard.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "market_report_metric")
@Getter
@Setter
public class MarketReportMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", insertable = false, updatable = false)
    private Long reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id")
    private MarketReport report;

    @Column(name = "metric_name", length = 200)
    private String metricName;

    @Column(name = "value", length = 300)
    private String value;

    @Column(name = "unit", length = 50)
    private String unit;

    @Column(name = "qoq_change", length = 100)
    private String qoqChange;

    @Column(name = "yoy_change", length = 100)
    private String yoyChange;

    @Column(name = "note", length = 1000)
    private String note;
}
