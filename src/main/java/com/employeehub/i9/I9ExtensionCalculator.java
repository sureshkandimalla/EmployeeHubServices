package com.employeehub.i9;

import com.employeehub.model.i9.I9Record;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

// The 7 work-authorization extension calculators from spec section 5.3's
// table — each reads its own sub-fields out of I9Record.extensionDetailsJson
// (a plain string-keyed JSON object; see I9Record's javadoc for why a blob
// instead of ~20 mostly-null columns). Each type has a matching `xError`
// method that both `apply` and `conditionError` consult, so the two never
// drift apart: `apply` returns null (falls back to the un-extended base
// date in I9Rules.reverifyBy) whenever its own `xError` is non-null, and
// `conditionError` surfaces that same message as I9Rules.validate's E20.
final class I9ExtensionCalculator {

    private I9ExtensionCalculator() {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    record Result(LocalDate reverifyBy) {
    }

    private static Map<String, String> details(I9Record record) {
        String json = record.getExtensionDetailsJson();
        if (json == null || json.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static LocalDate date(Map<String, String> details, String key) {
        String value = details.get(key);
        if (value == null || value.isBlank()) return null;
        return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private static boolean bool(Map<String, String> details, String key) {
        return Boolean.parseBoolean(details.get(key));
    }

    static Result apply(I9Record record, LocalDate baseBeforeExtension) {
        if (record.getExtensionType() == null) return null;
        I9Rules.ExtensionType type = I9Rules.ExtensionType.valueOf(record.getExtensionType());
        Map<String, String> d = details(record);

        return switch (type) {
            case EAD_AUTO_EXT -> eadAutoExtensionError(record, d) != null ? null : eadAutoExtension(record, d);
            case H1B_240_DAY -> h1b240DayError(record, d) != null ? null : h1b240Day(record, d);
            case H1B_PORTABILITY_AC21 -> h1bPortabilityAc21Error(record, d) != null ? null : h1bPortabilityAc21(record, d);
            case STEM_OPT_180_DAY -> stemOpt180DayError(record, d) != null ? null : stemOpt180Day(record, d);
            case CAP_GAP -> capGapError(record, d) != null ? null : capGap(record, d);
            case TPS_FEDERAL_REGISTER -> tpsFederalRegister(d);
            case GREEN_CARD_EXTENSION -> null; // Always "Not required" — see I9Rules.neverReverify.
        };
    }

    // Returns null if the extension is valid (no error); otherwise the
    // exact message I9Rules.validate attaches to E20.
    static String conditionError(I9Record record) {
        if (record.getExtensionType() == null) return null;
        I9Rules.ExtensionType type = I9Rules.ExtensionType.valueOf(record.getExtensionType());
        Map<String, String> d = details(record);

        return switch (type) {
            case EAD_AUTO_EXT -> eadAutoExtensionError(record, d);
            case H1B_240_DAY -> h1b240DayError(record, d);
            case H1B_PORTABILITY_AC21 -> h1bPortabilityAc21Error(record, d);
            case STEM_OPT_180_DAY -> stemOpt180DayError(record, d);
            case CAP_GAP -> capGapError(record, d);
            case TPS_FEDERAL_REGISTER -> d.get("noticeCitation") == null || d.get("noticeCitation").isBlank()
                    ? "Enter the TPS Federal Register notice citation"
                    : null;
            case GREEN_CARD_EXTENSION -> null;
        };
    }

    // ───────────────────────────── EAD auto-extension (I-797C) ─────────────

    private static Result eadAutoExtension(I9Record record, Map<String, String> d) {
        LocalDate cardExpiry = record.getListA().getExpiry();
        LocalDate extended = cardExpiry.plusDays(I9Config.EAD_AUTO_EXT_DAYS);
        String categoryOnReceipt = d.get("categoryOnReceipt");
        if (I9Config.SPOUSE_EAD_CATEGORIES.contains(categoryOnReceipt)) {
            LocalDate i94End = record.getI94() != null ? record.getI94().getAdmitUntil() : null;
            if (i94End != null && i94End.isBefore(extended)) extended = i94End;
        }
        return new Result(extended);
    }

    private static String eadAutoExtensionError(I9Record record, Map<String, String> d) {
        LocalDate cardExpiry = record.getListA() != null ? record.getListA().getExpiry() : null;
        LocalDate received = date(d, "receivedDate");
        if (cardExpiry == null || received == null) return "Enter the EAD auto-extension receipt details";
        if (!received.isBefore(I9Config.EAD_AUTO_EXT_CUTOFF)) {
            return "Renewals filed on or after 30-Oct-2025 get no automatic extension – reverify by the card expiry date";
        }
        if (!received.isBefore(cardExpiry)) {
            return "The extension receipt must be received before the card expired";
        }
        String onCard = d.get("categoryOnCard");
        String onReceipt = d.get("categoryOnReceipt");
        boolean bothTps = ("A12".equals(onCard) || "C19".equals(onCard)) && ("A12".equals(onReceipt) || "C19".equals(onReceipt));
        if (onCard != null && onReceipt != null && !onCard.equals(onReceipt) && !bothTps) {
            return "EAD auto-extension requires the receipt category to match the card (or both be TPS A12/C19)";
        }
        return null;
    }

    // ───────────────────────────── H-1B 240-day ─────────────────────────────

    private static Result h1b240Day(I9Record record, Map<String, String> d) {
        LocalDate i94Expiry = record.getI94().getAdmitUntil();
        LocalDate extended = i94Expiry.plusDays(I9Config.H1B_240_DAYS);
        LocalDate decision = date(d, "decisionDate");
        if (decision != null && decision.isBefore(extended)) extended = decision;
        return new Result(extended);
    }

    private static String h1b240DayError(I9Record record, Map<String, String> d) {
        LocalDate i94Expiry = record.getI94() != null ? record.getI94().getAdmitUntil() : null;
        if (i94Expiry == null) return "Enter the I-94 expiry this extension applies to";
        LocalDate filed = date(d, "filingDate");
        return (filed != null && filed.isAfter(i94Expiry))
                ? "The 240-day extension requires the petition to have been filed on or before the I-94 expiry date"
                : null;
    }

    // ───────────────────────────── H-1B portability (AC21) ──────────────────

    private static Result h1bPortabilityAc21(I9Record record, Map<String, String> d) {
        LocalDate priorI94Expiry = record.getI94().getAdmitUntil();
        String status = d.get("petitionStatus");
        LocalDate filed = date(d, "filingDate");
        if ("PENDING".equals(status) && filed != null && !filed.isAfter(priorI94Expiry)) {
            return new Result(priorI94Expiry.plusDays(I9Config.H1B_240_DAYS));
        }
        return new Result(priorI94Expiry);
    }

    private static String h1bPortabilityAc21Error(I9Record record, Map<String, String> d) {
        if (record.getI94() == null || record.getI94().getAdmitUntil() == null) {
            return "Enter the prior I-94 expiry this extension applies to";
        }
        // "NEW_HIRE only; filed before or on the first day" — the first
        // day of work is validFrom for a NEW_HIRE record (see
        // I9Rules.validFromLabel).
        if (!I9Rules.SubType.NEW_HIRE.name().equals(record.getSubtype())) {
            return "H-1B portability (AC21) can only be used on a New Hire I-9";
        }
        LocalDate filed = date(d, "filingDate");
        return (filed != null && record.getValidFrom() != null && filed.isAfter(record.getValidFrom()))
                ? "H-1B portability (AC21) requires the I-129 to have been filed on or before the first day of work"
                : null;
    }

    // ───────────────────────────── STEM OPT 180-day ─────────────────────────

    private static Result stemOpt180Day(I9Record record, Map<String, String> d) {
        LocalDate eadExpiry = record.getListA().getExpiry();
        LocalDate extended = eadExpiry.plusDays(I9Config.STEM_OPT_EXT_DAYS);
        LocalDate decision = date(d, "decisionDate");
        if (decision != null && decision.isBefore(extended)) extended = decision;
        return new Result(extended);
    }

    private static String stemOpt180DayError(I9Record record, Map<String, String> d) {
        LocalDate eadExpiry = record.getListA() != null ? record.getListA().getExpiry() : null;
        if (eadExpiry == null) return "Enter the OPT EAD expiry this extension applies to";
        LocalDate received = date(d, "receiptDate");
        if (received != null && received.isAfter(eadExpiry)) {
            return "The STEM OPT 180-day extension requires the I-765 receipt to be dated on or before the EAD expiry";
        }
        return !bool(d, "i20Endorsed") ? "The STEM OPT 180-day extension requires the I-20 to be endorsed for STEM" : null;
    }

    // ───────────────────────────── Cap-gap ───────────────────────────────────

    private static Result capGap(I9Record record, Map<String, String> d) {
        String status = d.get("petitionStatus");
        if ("DENIED".equals(status) || "WITHDRAWN".equals(status)) {
            LocalDate decision = date(d, "decisionDate");
            return decision != null ? new Result(decision) : null;
        }
        LocalDate approvedStart = date(d, "approvedStartDate");
        if ("APPROVED".equals(status) && approvedStart != null) {
            return new Result(approvedStart);
        }
        String fyStr = d.get("requestedStartFY");
        if (fyStr == null) return null;
        int fy = Integer.parseInt(fyStr);
        return new Result(LocalDate.of(fy + 1, 4, 1));
    }

    private static String capGapError(I9Record record, Map<String, String> d) {
        LocalDate eadExpiry = record.getListA() != null ? record.getListA().getExpiry() : null;
        LocalDate received = date(d, "cosReceiptDate");
        return (eadExpiry != null && received != null && received.isAfter(eadExpiry))
                ? "Cap-gap requires the H-1B change-of-status petition to have been filed before the OPT EAD expired"
                : null;
    }

    // ───────────────────────────── TPS Federal Register notice ──────────────

    private static Result tpsFederalRegister(Map<String, String> d) {
        LocalDate extendedThrough = date(d, "extendedThroughDate");
        return extendedThrough != null ? new Result(extendedThrough) : null;
    }

    // ───────────────────────────── Suggested notes (5.4) ─────────────────────

    static String suggestedNote(I9Rules.ExtensionType type, I9Record record) {
        Map<String, String> d = details(record);
        return switch (type) {
            case H1B_240_DAY -> "240-Day Ext. " + formatMdy(date(d, "filingDate"));
            case H1B_PORTABILITY_AC21 -> "AC-21 " + formatMdy(date(d, "filingDate"));
            case EAD_AUTO_EXT -> "EAD EXT";
            case CAP_GAP -> "CAP-GAP";
            case STEM_OPT_180_DAY -> "STEM OPT EXT";
            default -> null;
        };
    }

    private static String formatMdy(LocalDate date) {
        return date == null ? "" : date.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
    }
}
