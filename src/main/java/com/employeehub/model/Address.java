package com.employeehub.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@Table(name = "Address")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Address {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private long addressId;
  private String address;
  private String city;
  private String state;
  private String zipCode;
  private String country;

  // An employee can have many Address rows over time — each one is a period
  // they lived there. Updating the address doesn't overwrite a row; it
  // closes out whichever row is currently active (sets endDate, active =
  // false) and inserts a new one. Nullable so pre-existing rows (created
  // before this feature) don't need a backfill — AddressController treats
  // a null `active` as "currently active" for those.
  private LocalDate startDate;
  private LocalDate endDate;
  private Boolean active;

  // "HOME" (the default — null means HOME, for rows that predate this
  // field) or "WORKSITE". A WORKSITE row is tied to the specific Project it
  // was captured for (see `project` below) rather than competing with the
  // employee's home-address history.
  private String type;

  // "Remote"/"Hybrid"/"Onsite" — only meaningful on a WORKSITE row, set from
  // Project Onboarding's Work Arrangement field. Null on HOME rows and on
  // WORKSITE rows captured before this field existed.
  private String workArrangement;

  // Cuts the cycle Address -> project -> employee -> employee.address (a
  // List<Address> back to rows like this one) -> project -> ... by dropping
  // the nested Project's own `employee` field entirely — redundant anyway,
  // since this Address already has its own direct `employee` reference.
  @JsonIgnoreProperties("employee")
  @ManyToOne(fetch = FetchType.LAZY, optional = true)
  @JoinColumn(name = "project_id", referencedColumnName = "project_id", nullable = true)
  private Project project;

  // UI-only: the frontend sends a projectId when creating a WORKSITE
  // address during Project Onboarding; the controller resolves it into the
  // `project` relation above rather than requiring the caller to assemble
  // a whole Project object.
  @Transient
  private Long projectId;

  /*@OneToMany(cascade = CascadeType.ALL)
  @JoinColumn(name = "addressId")
  private List<EmployeeAddress> employeeAddress;*/
	/*
	 * @OneToMany(cascade = CascadeType.ALL)
	 * 
	 * @JoinColumn(name = "addressId") private List<Notes> addressNotes;
	 */
  
  @JsonIgnoreProperties("address")
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "employee_id", referencedColumnName = "employee_id")
  private Employee employee;

  @UpdateTimestamp
  private LocalDate LastUpdated;

@Override
public String toString() {
	return "Address [addressId=" + addressId + ", address=" + address + ", city=" + city + ", state=" + state
			+ ", zipCode=" + zipCode + ", country=" + country + ", LastUpdated="
			+ LastUpdated + "]";
}
  
  
}
