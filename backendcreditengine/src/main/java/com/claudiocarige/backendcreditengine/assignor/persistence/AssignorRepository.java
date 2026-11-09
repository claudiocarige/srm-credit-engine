package com.claudiocarige.backendcreditengine.assignor.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignorRepository extends JpaRepository<Assignor, UUID> {

    List<Assignor> findAllByOrderByNameAsc();
}
