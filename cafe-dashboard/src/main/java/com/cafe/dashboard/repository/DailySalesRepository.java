package com.cafe.dashboard.repository;

import com.cafe.dashboard.entity.DailySales;
import com.cafe.dashboard.entity.DailySalesId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailySalesRepository extends JpaRepository<DailySales, DailySalesId> {

    Optional<DailySales> findByStoreIdAndSaleDate(String storeId, LocalDate saleDate);

    List<DailySales> findByStoreIdAndSaleDateBetweenOrderBySaleDateDesc(String storeId, LocalDate from, LocalDate to);

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM DailySales d " +
           "WHERE d.storeId = :storeId AND d.saleDate BETWEEN :from AND :to")
    long sumAmount(String storeId, LocalDate from, LocalDate to);

    void deleteByStoreIdAndSaleDate(String storeId, LocalDate saleDate);
}
