import { createApi } from "@reduxjs/toolkit/query/react";
import { apiClient } from "@/services/api";
import type { CouponDetail, CouponList } from "./model";

export type PharmacyOption = {
  pharmacyId: string;
  name: string | null;
  phone: string;
};

export const couponsApi = createApi({
  reducerPath: "couponsApi",
  baseQuery: apiClient,
  tagTypes: ["Coupons"],
  endpoints: (build) => ({
    listCoupons: build.query<CouponList, { page: number; size: number }>({
      query: ({ page, size }) => ({ url: "/api/v1/coupons", params: { page, size } }),
      providesTags: ["Coupons"],
    }),
    readCoupon: build.query<{ coupon: CouponDetail }, string>({
      query: (id) => ({ url: `/api/v1/coupons/${id}` }),
      providesTags: ["Coupons"],
    }),
    saveCoupon: build.mutation<{ coupon: CouponDetail }, { id?: string; body: FormData }>({
      query: ({ id, body }) => ({
        url: id ? `/api/v1/coupons/${id}` : "/api/v1/coupons",
        method: id ? "PUT" : "POST",
        body,
      }),
      invalidatesTags: ["Coupons"],
    }),
    listCouponPharmacies: build.query<
      { owners: PharmacyOption[]; page: number; size: number; total: number },
      { page: number; size: number }
    >({
      query: ({ page, size }) => ({ url: "/pharmacy-owners", params: { page, size } }),
    }),
  }),
});

export const {
  useListCouponsQuery,
  useReadCouponQuery,
  useSaveCouponMutation,
  useListCouponPharmaciesQuery,
} = couponsApi;
