import { beforeEach, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { createDemoData } from "../data/seed";
import { OptimizerGenerateDialog } from "../features/meals/components/PlanTools";
import { FoodDetail } from "../features/catalog/components/FoodDetail";
import { addDays } from "../utils";

const mocks = vi.hoisted(() => ({ reload: vi.fn(), update: vi.fn(), toast: vi.fn() }));
let workspace = createDemoData();
vi.mock("../lib/config", () => ({ API_MODE: "api", RAG_ENABLED: true }));
vi.mock("../store/UserDataProvider", () => ({ useApp: () => ({ userData: workspace, ...mocks }) }));

beforeEach(() => {
  vi.clearAllMocks();
  workspace = structuredClone(createDemoData());
  workspace.plans = [];
  workspace.favorites = [];
  workspace.disliked = [];
  mocks.reload.mockResolvedValue(undefined);
  sessionStorage.setItem("nutriguide.token", "test-token");
});

it("the new weekly form sends its selected mode, JWT and profile version through the Java tools endpoint", async () => {
  const rows = Array.from({ length: 7 }, (_, day) => ({ planId: day + 100, planDate: addDays("2026-11-26", day), solverStatus: "FEASIBLE" }));
  const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify(rows), { status: 200, headers: { "Content-Type": "application/json" } }));
  vi.stubGlobal("fetch", fetcher);
  render(<MemoryRouter><OptimizerGenerateDialog date="2026-11-26" onClose={() => {}} /></MemoryRouter>);
  fireEvent.click(screen.getByRole("button", { name: "Bảy ngày" }));
  const dialog = screen.getByRole("dialog");
  fireEvent.change(within(dialog).getByLabelText("Chế độ thực đơn"), { target: { value: "GENERAL_REFERENCE" } });
  fireEvent.click(within(dialog).getByRole("button", { name: "Tạo thực đơn bảy ngày" }));
  await screen.findByText("Kết quả theo ngày");
  expect(screen.getAllByRole("link", { name: "Xem thực đơn" })).toHaveLength(7);
  expect(fetcher).toHaveBeenCalledOnce();
  const [url, init] = fetcher.mock.calls[0];
  expect(url).toBe(`/api/meal-plans/tools/users/me/meal-plans/generate-weekly?profileVersion=${workspace.profile.version}`);
  expect(new Headers(init.headers).get("Authorization")).toBe("Bearer test-token");
  expect(JSON.parse(init.body)).toMatchObject({ startDate: "2026-11-26", dailyTargetTemplate: { planningMode: "GENERAL_REFERENCE", targetCaloriesKcal: 1600 } });
  expect(mocks.reload).toHaveBeenCalledWith(true);
});

it("clearing a disliked food uses DELETE for dislike rather than deleting an unrelated favourite", async () => {
  const food = workspace.foods[0];
  workspace.disliked = [food.id];
  const fetcher = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
  vi.stubGlobal("fetch", fetcher);
  render(<MemoryRouter><FoodDetail food={food} onClose={() => {}} /></MemoryRouter>);
  fireEvent.click(screen.getByRole("button", { name: "Bỏ không thích" }));
  await waitFor(() => expect(mocks.update).toHaveBeenCalled());
  expect(fetcher).toHaveBeenCalledWith(`/api/foods/tools/users/me/dishes/${food.id}/dislike`, expect.objectContaining({ method: "DELETE" }));
  const next = mocks.update.mock.calls[0][0](workspace);
  expect(next.disliked).toEqual([]);
});
