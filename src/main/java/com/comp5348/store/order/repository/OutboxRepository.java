package com.comp5348.store.order.repository;

import com.comp5348.store.order.model.OutboxEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findBySentFalseOrderByCreatedAtAsc();
}
