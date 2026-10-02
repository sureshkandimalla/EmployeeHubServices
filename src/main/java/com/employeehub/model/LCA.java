package com.employeehub.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDate;

@Setter
@Getter
@Entity
@Table(name = "lca")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class LCA {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private long lcaId;
  private String jobTitle;
  private String lcaCaseNumber ;
  private LocalDate employmentStartDate;
  private LocalDate employmentEndDate;
  private LocalDate lcaPostedFromDate;
  private LocalDate lcaPostedToDate;
  private String socCode;
  private Long lcaWage;
  private String wageLevel;
  private String mangerId;
  private String jobLocation;
  private String jobLocation2;
  private String noticePostedLocation;
  private String noticePostedLocation2;
  private String lcaNumber;
  private String client;
  @Column(name = "vendor")
  private String customer;
  private LocalDate certifiedDate;
  private String status;
  private String shortName;
  @JsonIgnoreProperties("visa")
  @ManyToOne(fetch = FetchType.EAGER, optional = true)
  @JoinColumn(name = "employee_id", referencedColumnName = "employee_id", nullable = true)
  private Employee employee;

  // UI-only: the Employee select on the LCA form sends a plain employeeId
  // rather than a full Employee object — LCAService resolves this into the
  // `employee` relation above via a real lookup, rather than relying on
  // Jackson to deserialize a partial Employee from just an ID.
  @Transient
  private Long employeeId;

  @JsonIgnoreProperties("lca")
  @OneToOne(mappedBy = "lca", fetch = FetchType.LAZY, optional = true)
  private Visa visa;

  @UpdateTimestamp
  private LocalDate LastUpdated;


}
