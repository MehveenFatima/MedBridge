package com.medbridge.service;

import com.medbridge.dto.SourceSystemResponse;
import com.medbridge.mapper.BatchMapper;
import com.medbridge.repository.SourceSystemRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SourceSystemService {

    private final SourceSystemRepository repository;
    private final BatchMapper mapper;

    public SourceSystemService(SourceSystemRepository repository, BatchMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public List<SourceSystemResponse> findAll() {
        return repository.findAll(Sort.by("id")).stream().map(mapper::toSourceSystemResponse).toList();
    }
}
