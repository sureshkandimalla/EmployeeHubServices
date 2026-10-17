package com.employeehub.controller;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.exception.DataNotSavedException;
import com.employeehub.model.Adjustment;
import com.employeehub.repository.AdjustmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/v1/adjustment")
public class AdjustmentController {
	
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AdjustmentController.class);
	
	@Autowired
    private AdjustmentRepository adjustmentRepository;

	
    @GetMapping("/getAdjustments")
	public List<com.employeehub.domain.Adjustment> getAdjustments() {
		CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
    	return adjustmentRepository.findAllAdjustmentsWithNames().stream()
				.map(com.employeehub.domain.Adjustment::new)
				.collect(Collectors.toList());

    }
	@GetMapping("/findAdjustmentsByEmployeeId")
	public List<com.employeehub.domain.Adjustment> findAdjustmentsByEmployeeId(@RequestParam(required = true) long id) {
		CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
		return adjustmentRepository.findAdjustmentsByEmployeeId(id).stream()
				.map(row -> {
					com.employeehub.domain.Adjustment adjustment = new com.employeehub.domain.Adjustment(row);
					if (adjustment.getToId() == id) {
						adjustment.setAmount(-Math.abs(adjustment.getAmount()));
					}
					return adjustment;
				})
				.collect(Collectors.toList());

	}
	@PostMapping("/addAdjustment")
	public ResponseEntity<Adjustment> addAdjustment(@RequestBody Adjustment adjustment) throws DataNotSavedException {
		CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
		try {
			 Adjustment newAdjustment=adjustmentRepository.save(adjustment);

			return  ResponseEntity.ok(newAdjustment);
		} catch (Exception e) {
			throw new DataNotSavedException("Adjustment data not saved", e);
		}
	}
}
