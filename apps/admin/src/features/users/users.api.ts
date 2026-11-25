import { createApi } from "@reduxjs/toolkit/query/react";
import { apiClient } from "@/services/api";
import type { PlatformStaff, StaffRole } from "./model";

export type CreateStaffBody = {
  phone: string;
  displayName: string;
  subtypes: StaffRole[];
};

export type UpdateStaffBody = {
  displayName?: string;
  subtypes?: StaffRole[];
  disabled?: boolean;
};

export const usersApi = createApi({
  reducerPath: "usersApi",
  baseQuery: apiClient,
  tagTypes: ["Staff"],
  endpoints: (build) => ({
    listStaff: build.query<PlatformStaff[], void>({
      query: () => ({ url: "/staff" }),
      providesTags: ["Staff"],
    }),
    createStaff: build.mutation<PlatformStaff, CreateStaffBody>({
      query: (body) => ({ url: "/staff", method: "POST", body }),
      invalidatesTags: ["Staff"],
    }),
    updateStaff: build.mutation<PlatformStaff, { id: string; body: UpdateStaffBody }>({
      query: ({ id, body }) => ({ url: `/staff/${id}`, method: "PATCH", body }),
      invalidatesTags: ["Staff"],
    }),
  }),
});

export const { useListStaffQuery, useCreateStaffMutation, useUpdateStaffMutation } = usersApi;
