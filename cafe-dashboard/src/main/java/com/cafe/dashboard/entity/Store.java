package com.cafe.dashboard.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "stores")
@Getter
@Setter
public class Store {

    @Id
    @Column(name = "store_id", length = 10)
    private String storeId;

    @Column(name = "name", length = 200)
    private String name;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "subway_info", length = 200)
    private String subwayInfo;

    @Lob
    @Column(name = "business_hours")
    private String businessHours;

    @Column(name = "naver_id", length = 20)
    private String naverId;

    @Column(name = "lat")
    private Double lat;

    @Column(name = "lng")
    private Double lng;
}
