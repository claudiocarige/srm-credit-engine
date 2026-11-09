package com.claudiocarige.backendcreditengine.assignor.application;

import com.claudiocarige.backendcreditengine.assignor.persistence.Assignor;
import com.claudiocarige.backendcreditengine.assignor.persistence.AssignorRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignorService {

    private final AssignorRepository assignorRepository;

    public AssignorService(AssignorRepository assignorRepository) {
        this.assignorRepository = assignorRepository;
    }

    @Transactional(readOnly = true)
    public List<Assignor> listAssignors() {
        return assignorRepository.findAllByOrderByNameAsc();
    }
}
