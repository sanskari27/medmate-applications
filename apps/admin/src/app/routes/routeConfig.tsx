import type { ReactNode } from "react";
import type { NavEntry } from "@/components/layout";
import {
  IconCheck,
  IconDashboard,
  IconGlobe,
  IconHelp,
  IconReceipt,
  IconRx,
  IconStore,
  IconTag,
  IconUsers,
} from "@/components/ui/Icon";
import type { Account } from "@/types";
import {
  canManageCatalog,
  canManageCoupons,
  canManageUsers,
  canPrepareInventory,
  canReview,
  canSupport,
} from "@/features/authentication";
import { AppSectionsPage } from "@/features/appSections";
import { CatalogPage } from "@/features/catalog";
import { CouponsPage } from "@/features/coupons";
import { PrepListPage } from "@/features/prep";
import { DashboardPage } from "@/features/dashboard";
import { LeadsPage } from "@/features/leads";
import { PharmaciesPage } from "@/features/pharmacies";
import { ReviewsPage } from "@/features/reviews";
import { SupportPage } from "@/features/support";
import { UsersPage } from "@/features/users";

export type AppRoute = NavEntry & { element: ReactNode };

export const appRoutes: AppRoute[] = [
  {
    path: "/dashboard",
    label: "Home",
    title: "Home",
    sub: "Platform",
    icon: <IconDashboard size={19} />,
    element: <DashboardPage />,
  },
  {
    path: "/pharmacies",
    label: "Pharmacies",
    title: "Pharmacies",
    sub: "Owners",
    group: "Pharmacies",
    icon: <IconStore size={19} />,
    element: <PharmaciesPage />,
  },
  {
    path: "/reviews",
    label: "Reviews",
    title: "Reviews",
    sub: "Applications",
    icon: <IconCheck size={19} />,
    element: <ReviewsPage />,
  },
  {
    path: "/support",
    label: "Support",
    title: "Support",
    sub: "FAQs and cases",
    group: "Support",
    icon: <IconHelp size={19} />,
    element: <SupportPage />,
  },
  {
    path: "/leads",
    label: "Leads",
    title: "Leads",
    sub: "Website forms",
    group: "Leads",
    icon: <IconGlobe size={19} />,
    element: <LeadsPage />,
  },
  {
    path: "/catalog",
    label: "Manage Catalog",
    title: "Manage Catalog",
    sub: "Drugs and FMCG",
    group: "Catalog",
    icon: <IconRx size={19} />,
    element: <CatalogPage />,
  },
  {
    path: "/app-sections",
    label: "App sections",
    title: "App sections",
    sub: "Mobile categories",
    group: "Catalog",
    icon: <IconRx size={19} />,
    element: <AppSectionsPage />,
  },
  {
    path: "/coupons",
    label: "Coupons",
    title: "Coupons",
    sub: "Customer discounts",
    group: "Coupons",
    icon: <IconTag size={19} />,
    element: <CouponsPage />,
  },
  {
    path: "/inventory-prep",
    label: "Prepare Inventory",
    title: "Prepare Inventory",
    sub: "Opening stock",
    icon: <IconReceipt size={19} />,
    element: <PrepListPage />,
  },
  {
    path: "/users",
    label: "Manage Staffs",
    title: "Manage Staffs",
    sub: "Platform staff",
    group: "Staff management",
    icon: <IconUsers size={19} />,
    element: <UsersPage />,
  },
];

export function navRoutes(account: Account | null | undefined): AppRoute[] {
  return appRoutes.filter((route) => {
    if (route.path === "/reviews") return canReview(account);
    if (route.path === "/support" || route.path === "/leads") return canSupport(account);
    if (route.path === "/catalog" || route.path === "/app-sections")
      return canManageCatalog(account);
    if (route.path === "/coupons") return canManageCoupons(account);
    if (route.path === "/inventory-prep") return canPrepareInventory(account);
    if (route.path === "/users") return canManageUsers(account);
    return true;
  });
}
