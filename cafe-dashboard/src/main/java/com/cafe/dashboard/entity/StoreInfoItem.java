package com.cafe.dashboard.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "store_info_items")
@Getter
@Setter
public class StoreInfoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", length = 10, insertable = false, updatable = false)
    private String storeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @Column(name = "section", length = 50)
    private String section;

    @Column(name = "item_text", length = 500)
    private String itemText;

    @Column(name = "detail", length = 1000)
    private String detail;
}
