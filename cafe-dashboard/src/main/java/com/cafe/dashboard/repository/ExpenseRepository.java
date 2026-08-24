package com.cafe.dashboard.repository;

import com.cafe.dashboard.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    Page<Expense> findByStoreIdOrderByExpenseDateDescIdDesc(String storeId, Pageable pageable);

    List<Expense> findByStoreIdAndExpenseDateBetween(String storeId, LocalDate from, LocalDate to);

    long countByStoreIdAndExpenseDateBetween(String storeId, LocalDate from, LocalDate to);

    boolean existsByFixedCostIdAndExpenseDateBetween(Long fixedCostId, LocalDate from, LocalDate to);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e " +
           "WHERE e.storeId = :storeId AND e.expenseDate BETWEEN :from AND :to")
    long sumAmount(String storeId, LocalDate from, LocalDate to);

    @Query("SELECT e.category AS category, COALESCE(SUM(e.amount), 0) AS total FROM Expense e " +
           "WHERE e.storeId = :storeId AND e.expenseDate BETWEEN :from AND :to " +
           "GROUP BY e.category ORDER BY SUM(e.amount) DESC")
    List<CategoryTotal> sumByCategory(String storeId, LocalDate from, LocalDate to);

    interface CategoryTotal {
        String getCategory();
        Long getTotal();
    }
}
