package com.employeehub.service;

import com.employeehub.model.Employee;
import com.employeehub.model.LCA;
import com.employeehub.model.Visa;
import com.employeehub.repository.EmployeeRepository;
import com.employeehub.repository.LCARepository;
import com.employeehub.repository.VisaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class LCAService {

    @Autowired
    private VisaRepository visaRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    // An LCA can be tied to an employee two ways: directly (LCA.employee_id)
    // or indirectly through their Visa record (Visa.employee_id -> Visa.lca)
    // — the latter is how LCAs created from the Visa/LCA intake flow end up
    // linked, and LCADetails.jsx's own employee-name resolution already
    // falls back to it (see getEmployeeName there). Merging both sources
    // here (deduped by lcaId) means this is the one place that needs to
    // know about that, rather than every caller of getLCAsByEmployeeId.
    public List<LCA> getLCAsByEmployeeId(Long employeeId) {
        Map<Long, LCA> merged = new LinkedHashMap<>();
        lcaRepository.findByEmployee_EmployeeId(employeeId)
                .forEach(lca -> merged.put(lca.getLcaId(), lca));
        visaRepository.findByEmployee_EmployeeId(employeeId).stream()
                .map(Visa::getLca)
                .filter(lca -> lca != null)
                .forEach(lca -> merged.put(lca.getLcaId(), lca));
        return List.copyOf(merged.values());
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
        // The form's Employee select sends a plain employeeId, not a full
        // Employee object — resolve it to a real managed entity rather than
        // trust whatever Jackson did with lca.getEmployee() (nothing, since
        // LCA has no employee object in its own JSON shape coming from the
        // frontend).
        Employee resolvedEmployee = lca.getEmployeeId() != null
                ? employeeRepository.findById(lca.getEmployeeId()).orElse(null)
                : lca.getEmployee();

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
            if (resolvedEmployee != null) existing.setEmployee(resolvedEmployee);
            return lcaRepository.save(existing);
        }
        // CREATE
        lca.setEmployee(resolvedEmployee);
        return lcaRepository.save(lca);
    }

    public void deleteLCA(Long id) {
        lcaRepository.deleteById(id);
    }
}
