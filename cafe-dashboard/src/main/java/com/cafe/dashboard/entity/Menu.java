package com.cafe.dashboard.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "menu")
@Getter
@Setter
public class Menu {

    @Id
    @Column(name = "menu_id", length = 10)
    private String menuId;

    @Column(name = "store_id", length = 10, insertable = false, updatable = false)
    private String storeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @Column(name = "menu_name", length = 300)
    private String menuName;

    @Column(name = "price_krw")
    private Integer priceKrw;

    @Column(name = "price_note", length = 50)
    private String priceNote;
}
