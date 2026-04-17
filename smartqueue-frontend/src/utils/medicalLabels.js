export const SERVICE_TYPE_OPTIONS = [
    { value: "AADHAAR_UPDATE", label: "General Consultation" },
    { value: "PAN_CARD", label: "Follow-up Visit" },
    { value: "PASSPORT", label: "Specialist Consultation" },
    { value: "DRIVING_LICENSE", label: "Emergency" },
    { value: "INCOME_CERTIFICATE", label: "Lab/Test Review" },
    { value: "OTHER", label: "Other Consultation" }
];

export const VISIT_TYPE_LABELS = {
    WALK_IN: "Walk-in",
    APPOINTMENT: "Appointment",
    FOLLOW_UP: "Follow-up"
};

export function getMedicalServiceLabel(serviceType) {
    return SERVICE_TYPE_OPTIONS.find((option) => option.value === serviceType)?.label
        || "Other Consultation";
}

export function getVisitTypeLabel(visitType) {
    return VISIT_TYPE_LABELS[visitType] || "Queue Entry";
}
