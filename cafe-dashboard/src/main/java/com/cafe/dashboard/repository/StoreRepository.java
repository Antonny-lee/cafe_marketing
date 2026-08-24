package com.cafe.dashboard.repository;

import com.cafe.dashboard.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StoreRepository extends JpaRepository<Store, String> {

    @Query("SELECT s FROM Store s WHERE :keyword IS NULL OR :keyword = '' " +
           "OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(s.address) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Store> search(@Param("keyword") String keyword);
}
