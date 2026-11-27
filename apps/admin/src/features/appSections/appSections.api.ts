import { createApi } from "@reduxjs/toolkit/query/react";
import { apiClient } from "@/services/api";
import type { AppSection, AppSectionList } from "./model";

export const appSectionsApi = createApi({
  reducerPath: "appSectionsApi",
  baseQuery: apiClient,
  tagTypes: ["AppSections"],
  endpoints: (build) => ({
    listAppSections: build.query<AppSectionList, void>({
      query: () => ({ url: "/api/v1/app-sections" }),
      providesTags: ["AppSections"],
    }),
    listCatalogCategories: build.query<{ categories: string[] }, void>({
      query: () => ({ url: "/api/v1/catalog/categories" }),
    }),
    saveAppSection: build.mutation<{ section: AppSection }, { id?: string; body: FormData }>({
      query: ({ id, body }) => ({
        url: id ? `/api/v1/app-sections/${id}` : "/api/v1/app-sections",
        method: id ? "PUT" : "POST",
        body,
      }),
      invalidatesTags: ["AppSections"],
    }),
    deleteAppSection: build.mutation<void, string>({
      query: (id) => ({ url: `/api/v1/app-sections/${id}`, method: "DELETE" }),
      invalidatesTags: ["AppSections"],
    }),
  }),
});

export const {
  useListAppSectionsQuery,
  useListCatalogCategoriesQuery,
  useSaveAppSectionMutation,
  useDeleteAppSectionMutation,
} = appSectionsApi;
