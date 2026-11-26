export const API_MODE =
  import.meta.env.VITE_API_MODE === "demo" ? "demo" : "api";
export const RAG_ENABLED =
  API_MODE === "api" || import.meta.env.VITE_RAG_ENABLED === "true";
