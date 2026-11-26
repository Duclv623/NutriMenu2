import { json, request } from "../../../lib/api";
import type { CheckIn } from "../../../types";

export const activityService = {
  getShopping: () => request<string[]>("/me/shopping-list"),
  getCheckIns: () => request<CheckIn[]>("/me/check-ins"),
  checkIn: (entry: CheckIn) =>
    request<CheckIn>("/me/check-ins", { method: "POST", body: json(entry) }),
  deleteCheckIn: (id: string) =>
    request<void>(`/me/check-ins/${encodeURIComponent(id)}`, {
      method: "DELETE",
    }),
  shopping: (checkedIds: string[]) =>
    request<void>("/me/shopping-list", {
      method: "PUT",
      body: json({ checkedIds }),
    }),
};
