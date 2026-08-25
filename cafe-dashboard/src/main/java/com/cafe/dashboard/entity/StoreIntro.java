package com.cafe.dashboard.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "store_intro")
@Getter
@Setter
public class StoreIntro {

    @Id
    @Column(name = "store_id", length = 10)
    private String storeId;

    @Column(name = "intro_text")
    private String introText;
}
