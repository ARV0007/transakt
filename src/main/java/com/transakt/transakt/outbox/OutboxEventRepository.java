package com.transakt.transakt.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    List<OutboxEvent> findByPublishedAtIsNullOrderByCreatedAtAsc();

    /**
     * One DELETE statement, not a load-then-delete loop.
     *
     * A derived deleteByPublishedAtBefore(...) reads like a single statement and is
     * not: Spring Data SELECTs every matching row into memory and issues one DELETE
     * per row. On a table you are cleaning BECAUSE it got big, that is the worst
     * possible shape. @Modifying @Query sends one statement and materialises nothing.
     *
     * publishedAt IS NOT NULL is the load-bearing clause. An unpublished row is one
     * that has never reached a merchant, and it is also the oldest row in the table,
     * so a predicate on age alone would delete exactly the events that still matter.
     */
    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.publishedAt IS NOT NULL AND e.publishedAt < :cutoff")
    int deletePublishedBefore(@Param("cutoff") Instant cutoff);
}