import { json, request } from "../../../lib/api";
import type { Profile } from "../../../types";

export const profileService = {
  get: () => request<Profile>("/me/profile"),
  save: (profile: Profile) => request<Profile>("/me/profile", { method: "PUT", body: json(profile) }),
};
