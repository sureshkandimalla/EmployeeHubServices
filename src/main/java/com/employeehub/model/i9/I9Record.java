package com.employeehub.model.i9;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

// One Form I-9 action for one employee — a New Hire I-9, a Supplement B
// reverification, a rehire, a correction, etc. (subtype, see I9Rules.SubType
// for the full list and what each means). Deliberately NOT part of the
// generic Document table: a Document row is one flat (type, subType,
// validFrom, validUntil, description) tuple, which has no room for List
// A/B/C, extensions, Section 1 attestation, E-Verify, and the rest of what
// an I-9 actually needs to track. Attachments (the Form I-9 PDF, document
// copies, I-797, ...) stay on the existing generic Document table instead,
// tagged entityType="I9Record"/entityId=this row's id — see
// DocumentService#resolveRootFolder's "I9Record" case — so a single upload
// of several files is still just several Document rows sharing one
// i9RecordId, not a second file-storage mechanism.
//
// Computed fields (section2Due, reverifyBy, i9Status, retainUntil, ...) are
// NOT stored here — I9Rules computes them fresh from this row (plus the
// employee's other I9Records) on every read, exactly like
// EmployeeReconcileController already does for reconciliation records.
@Setter
@Getter
@Entity
@Table(name = "I9Record")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class I9Record {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long employeeId;

    // The per-employee sequence number behind the <EmpID>-I<NN> record ID
    // (e.g. BIS0001-I03) — assigned once at creation, never reused even
    // after a delete, so it stays a stable reference once shown to a user.
    private Integer recordNo;

    // One of I9Rules.SubType: NEW_HIRE, REVERIFY, REHIRE_SUPP_B,
    // REHIRE_NEW, NAME_CHANGE, CORRECTION, DOC_UPDATE, MIGRATED, DESTROYED.
    private String subtype;

    private LocalDate validFrom;

    private LocalDate section1Date;
    // One of I9Rules.Attestation: CITIZEN, NONCITIZEN_NATIONAL, LPR,
    // NONCITIZEN_AUTHORIZED.
    private String attestation;
    // Null also stands for "N/A" (e.g. refugees/asylees) when attestation
    // = NONCITIZEN_AUTHORIZED but no fixed expiry applies.
    private LocalDate section1AuthExpiry;
    private Boolean under3Days;

    // Section 2 / Supplement B completion date. Null = still in process.
    private LocalDate completedOn;

    // One of I9Rules.DocOption: LIST_A, LIST_BC, LIST_C_ONLY.
    private String docOption;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "code", column = @Column(name = "list_a_code")),
            @AttributeOverride(name = "number", column = @Column(name = "list_a_number")),
            @AttributeOverride(name = "expiry", column = @Column(name = "list_a_expiry")),
            @AttributeOverride(name = "noExpiry", column = @Column(name = "list_a_no_expiry")),
    })
    private ListADetails listA = new ListADetails();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "number", column = @Column(name = "i94_number")),
            @AttributeOverride(name = "admitUntil", column = @Column(name = "i94_admit_until")),
            @AttributeOverride(name = "ds", column = @Column(name = "i94_ds")),
            @AttributeOverride(name = "authEndDate", column = @Column(name = "i94_auth_end_date")),
    })
    private I94Details i94 = new I94Details();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "code", column = @Column(name = "list_b_code")),
            @AttributeOverride(name = "number", column = @Column(name = "list_b_number")),
            @AttributeOverride(name = "expiry", column = @Column(name = "list_b_expiry")),
    })
    private ListBDetails listB = new ListBDetails();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "code", column = @Column(name = "list_c_code")),
            @AttributeOverride(name = "number", column = @Column(name = "list_c_number")),
            @AttributeOverride(name = "expiry", column = @Column(name = "list_c_expiry")),
            @AttributeOverride(name = "restrictedSsn", column = @Column(name = "list_c_restricted_ssn")),
    })
    private ListCDetails listC = new ListCDetails();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "type", column = @Column(name = "receipt_type")),
            @AttributeOverride(name = "validUntil", column = @Column(name = "receipt_valid_until")),
    })
    private ReceiptDetails receipt = new ReceiptDetails();

    // One of I9Rules.ExtensionType: EAD_AUTO_EXT, H1B_240_DAY,
    // H1B_PORTABILITY_AC21, STEM_OPT_180_DAY, CAP_GAP,
    // TPS_FEDERAL_REGISTER, GREEN_CARD_EXTENSION, or null (none).
    private String extensionType;

    // Each extension type has its own sub-fields (spec section 5.3's
    // table) — a JSON blob here avoids ~20 mostly-null columns for fields
    // that only ever apply to one extension type at a time. I9Rules parses
    // and validates it; nothing downstream trusts it unparsed.
    @Column(columnDefinition = "TEXT")
    private String extensionDetailsJson;

    private Boolean priorAuthExpired;
    private LocalDate originalI9Date;
    private Boolean remoteExam;
    private Boolean preparerUsed;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "caseNo", column = @Column(name = "everify_case_no")),
            @AttributeOverride(name = "created", column = @Column(name = "everify_created")),
            @AttributeOverride(name = "result", column = @Column(name = "everify_result")),
    })
    private EverifyDetails everify = new EverifyDetails();

    private Long correctsRecordId;
    private String correctionReason;

    private String destructionMethod;
    private String destroyedBy;

    // "Additional information" — free text, auto-suggested by
    // I9Rules.suggestedNote but always user-editable.
    @Column(columnDefinition = "TEXT")
    private String notes;

    private String createdBy;
    private LocalDateTime createdAt;
}
