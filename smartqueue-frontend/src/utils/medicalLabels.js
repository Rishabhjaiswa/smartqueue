export const SERVICE_TYPE_OPTIONS = [
    { value: "GENERAL", label: "General Consultation" },
    { value: "FOLLOW_UP", label: "Follow-up Visit" },
    { value: "SPECIALIST", label: "Specialist Consultation" },
    { value: "EMERGENCY", label: "Emergency" },
    { value: "LAB", label: "Lab/Test Review" },
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
