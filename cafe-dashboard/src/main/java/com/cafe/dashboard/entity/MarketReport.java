package com.cafe.dashboard.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "market_report")
@Getter
@Setter
public class MarketReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long reportId;

    @Column(name = "location", length = 300)
    private String location;

    @Column(name = "industry", length = 100)
    private String industry;

    @Column(name = "quarter", length = 20)
    private String quarter;

    @Column(name = "report_date")
    private LocalDate reportDate;

    @Lob
    @Column(name = "raw_text")
    private String rawText;

    @Lob
    @Column(name = "opinion_text")
    private String opinionText;
}
