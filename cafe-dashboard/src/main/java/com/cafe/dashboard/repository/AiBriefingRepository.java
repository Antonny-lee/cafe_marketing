package com.cafe.dashboard.repository;

import com.cafe.dashboard.entity.AiBriefing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiBriefingRepository extends JpaRepository<AiBriefing, Long> {
    List<AiBriefing> findByStoreIdOrderById(String storeId);
}
