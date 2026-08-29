package com.finme.backend.repository;

import com.finme.backend.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByUserIdOrderByCategoryAsc(Long userId);

    /**
     * Case-insensitive on purpose - see Budget.category. Relies on the unique constraint being
     * declared case-sensitively at the DB level while this lookup normalizes in Java
     * (BudgetService strips/compares before calling either this or save), so "Groceries" and
     * "groceries" cannot silently become two rows that both claim the same category.
     */
    Optional<Budget> findByUserIdAndCategoryIgnoreCase(Long userId, String category);

    void deleteByUserId(Long userId);
}
