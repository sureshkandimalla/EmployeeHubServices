package com.employeehub.service;

import com.employeehub.model.LCA;
import com.employeehub.repository.LCARepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class LCAService {
    public List<LCA> getLCAsByEmployeeId(Long employeeId) {
        return lcaRepository.findByEmployee_EmployeeId(employeeId);
    }

    @Autowired
    private LCARepository lcaRepository;

    public List<LCA> getAllLCAs() {
        return lcaRepository.findAll();
    }

    public Optional<LCA> getLCAById(Long id) {
        return lcaRepository.findById(id);
    }

    public LCA saveLCA(LCA lca) {
        if (lca.getLcaId() != 0 && lcaRepository.existsById(lca.getLcaId())) {
            // UPDATE — fetch existing and copy fields to preserve unchanged values
            LCA existing = lcaRepository.findById(lca.getLcaId()).get();
            existing.setJobTitle(lca.getJobTitle());
            existing.setLcaCaseNumber(lca.getLcaCaseNumber());
            existing.setEmploymentStartDate(lca.getEmploymentStartDate());
            existing.setEmploymentEndDate(lca.getEmploymentEndDate());
            existing.setLcaPostedFromDate(lca.getLcaPostedFromDate());
            existing.setLcaPostedToDate(lca.getLcaPostedToDate());
            existing.setSocCode(lca.getSocCode());
            existing.setLcaWage(lca.getLcaWage());
            existing.setWageLevel(lca.getWageLevel());
            existing.setMangerId(lca.getMangerId());
            existing.setJobLocation(lca.getJobLocation());
            existing.setJobLocation2(lca.getJobLocation2());
            existing.setNoticePostedLocation(lca.getNoticePostedLocation());
            existing.setNoticePostedLocation2(lca.getNoticePostedLocation2());
            existing.setLcaNumber(lca.getLcaNumber());
            existing.setClient(lca.getClient());
            existing.setCustomer(lca.getCustomer());
            existing.setCertifiedDate(lca.getCertifiedDate());
            existing.setStatus(lca.getStatus());
            existing.setShortName(lca.getShortName());
            if (lca.getEmployee() != null) existing.setEmployee(lca.getEmployee());
            return lcaRepository.save(existing);
        }
        // CREATE
        return lcaRepository.save(lca);
    }

    public void deleteLCA(Long id) {
        lcaRepository.deleteById(id);
    }
}
