package com.employeehub.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDate;
import java.util.List;

@Setter
@Getter
@Entity
@Table(name = "Wage")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Wage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private long wageId;

  // Read-only mirror of the project_id column — the FK is actually owned
  // from the other side (Project.billRates' @JoinColumn(name = "projectId")),
  // so this column exists on the table but was never mapped as a field
  // here. Needed to resolve a WorkOrderPO document's owning Project/
  // Employee without a second query (see DocumentService#resolveRootFolder).
  // Must reuse the exact logical name "projectId" the Project side already
  // registered — Hibernate treats a different logical name resolving to
  // the same physical column as a duplicate mapping and refuses to start.
  @Column(name = "projectId", insertable = false, updatable = false)
  private Long projectId;

  private float wage;
  private String wageType;
  private LocalDate startDate;
  private LocalDate EndDate;
  @OneToMany(cascade = CascadeType.ALL)
  @JoinColumn(name = "wageId")
  private List<Notes> wageNotes;


  private LocalDate createdDate;

  @UpdateTimestamp
  private LocalDate LastUpdated;

  @Override
  public String toString() {
    return "Wage{" +
            "wageId=" + wageId +
            ", wage=" + wage +
            ", startDate=" + startDate +
            ", EndDate=" + EndDate +
            ", wageNotes=" + wageNotes +
            ", createdDate=" + createdDate +
            ", LastUpdated=" + LastUpdated +
            '}';
  }
}
