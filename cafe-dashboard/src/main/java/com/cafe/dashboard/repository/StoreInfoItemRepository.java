package com.cafe.dashboard.repository;

import com.cafe.dashboard.entity.StoreInfoItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreInfoItemRepository extends JpaRepository<StoreInfoItem, Long> {
    List<StoreInfoItem> findByStoreIdOrderBySectionAscIdAsc(String storeId);
}
