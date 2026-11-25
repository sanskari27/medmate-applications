export const STAFF_ROLES = ["compliance", "finance", "sales", "support"] as const;

export type StaffRole = (typeof STAFF_ROLES)[number];

const ROLE_LABELS: Record<StaffRole, string> = {
  compliance: "Compliance",
  finance: "Finance",
  sales: "Sales",
  support: "Support",
};

export type PlatformStaff = {
  id: string;
  phone: string;
  displayName: string;
  subtypes: string[];
  disabled: boolean;
};

export type StaffDraft = {
  displayName: string;
  phone: string;
  subtypes: StaffRole[];
};

export function isStaffRole(value: string): value is StaffRole {
  return (STAFF_ROLES as readonly string[]).includes(value);
}

export function roleLabels(subtypes: string[]): string {
  const labels = STAFF_ROLES.filter((role) => subtypes.includes(role)).map(
    (role) => ROLE_LABELS[role],
  );
  if (labels.length === 0) return "No role";
  return labels.join(", ");
}

export function roleLabel(role: StaffRole): string {
  return ROLE_LABELS[role];
}
