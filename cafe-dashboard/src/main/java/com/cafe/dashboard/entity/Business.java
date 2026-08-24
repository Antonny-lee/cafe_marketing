package com.cafe.dashboard.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "business")
@Getter
@Setter
public class Business {

    @Id
    @Column(name = "biz_reg_no", length = 10)
    private String bizRegNo;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "store_id", length = 10)
    private String storeId;

    @Column(name = "ceo_name", length = 100)
    private String ceoName;

    @Column(name = "open_date", length = 8)
    private String openDate;

    @Column(name = "biz_name", length = 200)
    private String bizName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "verified", length = 1)
    private String verified;

    @Column(name = "biz_status", length = 20)
    private String bizStatus;

    @Column(name = "biz_status_code", length = 5)
    private String bizStatusCode;

    @Column(name = "tax_type", length = 50)
    private String taxType;

    @Column(name = "tax_type_code", length = 5)
    private String taxTypeCode;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;
}
