package com.cafe.dashboard.repository;

import com.cafe.dashboard.entity.ReviewCategoryTag;
import com.cafe.dashboard.entity.ReviewCategoryTagId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ReviewCategoryTagRepository extends JpaRepository<ReviewCategoryTag, ReviewCategoryTagId> {

    List<ReviewCategoryTag> findByIdStoreIdOrderByMentionCountDesc(String storeId);

    @Query("SELECT t.tagCategory AS tagCategory, SUM(t.mentionCount) AS totalMentions " +
           "FROM ReviewCategoryTag t GROUP BY t.tagCategory ORDER BY SUM(t.mentionCount) DESC")
    List<CategoryStat> aggregateByCategory();

    @Query("SELECT t FROM ReviewCategoryTag t ORDER BY t.mentionCount DESC")
    List<ReviewCategoryTag> findTopTags(Pageable pageable);

    List<ReviewCategoryTag> findByIdStoreIdInAndIdTagTextIn(List<String> storeIds, List<String> tagTexts);
}
