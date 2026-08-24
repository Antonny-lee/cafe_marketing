package com.cafe.dashboard.repository;

import com.cafe.dashboard.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuRepository extends JpaRepository<Menu, String> {
    List<Menu> findByStoreIdOrderByMenuId(String storeId);
}
