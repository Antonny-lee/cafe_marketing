package com.cafe.dashboard.repository;

import com.cafe.dashboard.entity.Business;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BusinessRepository extends JpaRepository<Business, String> {
    List<Business> findByOwnerUserId(Long ownerUserId);
}
