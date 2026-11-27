import { beforeEach, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { createDemoData } from "../data/seed";
import { addDays, localDate } from "../utils";
import type { Plan } from "../types";
import { MealPlannerPage } from "../features/meals/pages/MealPlannerPage";

const mocks = vi.hoisted(() => ({ generateWeekly: vi.fn(), update: vi.fn(), toast: vi.fn() }));
let workspace = createDemoData();
let template: Plan;
vi.mock("../store/UserDataProvider", () => ({ useApp: () => ({ userData: workspace, update: mocks.update, toast: mocks.toast }) }));
vi.mock("../lib/config", () => ({ API_MODE: "api", RAG_ENABLED: true }));
vi.mock("../features/meals/services/mealPlanService", () => ({ mealPlanService: { generateWeekly: mocks.generateWeekly } }));
beforeEach(() => {
  vi.clearAllMocks();
  workspace = structuredClone(createDemoData());
  workspace.foods.forEach(food => { food.recipeSource = `NIN_BULK_TEST_${food.id}`; food.solverEligible = true; });
  template = { ...workspace.plans[0], dataMode: "api", solverStatus: "FEASIBLE", planningMode: "GENERAL_REFERENCE" };
  workspace.plans = [];
  workspace.completedMeals = [];
  workspace.capabilities = { generate: true, weeklyGeneration: true, swap: true, favoriteRemoval: true, cookingTime: false, atomicVersions: true };
});
function open() {
  render(<MemoryRouter><MealPlannerPage /></MemoryRouter>);
  fireEvent.click(screen.getByRole("button", { name: "Tạo thực đơn" }));
  const dialog = screen.getByRole("dialog");
  fireEvent.change(within(dialog).getByLabelText("Chế độ thực đơn"), { target: { value: "GENERAL_REFERENCE" } });
  fireEvent.change(within(dialog).getByLabelText("Số ngày tạo thực đơn"), { target: { value: "7" } });
  return dialog;
}
it("generates seven days using the weekly API and keeps all seven returned plans", async () => {
  const created = Array.from({ length: 7 }, (_, index) => ({ ...template, id: String(100+index), date: addDays(localDate(),index) }));
  mocks.generateWeekly.mockResolvedValue(created);
  const dialog = open();
  fireEvent.click(within(dialog).getByRole("button", { name: "Tạo thực đơn 7 ngày" }));
  await waitFor(() => expect(mocks.update).toHaveBeenCalled());
  expect(mocks.generateWeekly).toHaveBeenCalledWith(expect.objectContaining({ date: localDate(), planningMode: "GENERAL_REFERENCE", maxMinutes: 0 }),workspace.profile.version);
  const apply = mocks.update.mock.calls[0][0];
  expect(apply(workspace).plans.map((plan: Plan) => plan.date)).toEqual(created.map(plan => plan.date));
  expect(mocks.toast).toHaveBeenCalledWith("Đã tạo và lưu thực đơn 7 ngày.");
});
it("blocks a conflicting saved day before submitting a weekly request", async () => {
  workspace.plans = [{ ...template, date: addDays(localDate(),2) }];
  const dialog = open();
  fireEvent.click(within(dialog).getByRole("button", { name: "Tạo thực đơn 7 ngày" }));
  await screen.findByText(/Khoảng 7 ngày đã có thực đơn/);
  expect(mocks.generateWeekly).not.toHaveBeenCalled();
  expect(mocks.update).not.toHaveBeenCalled();
});
it("does not replace local plans when weekly generation fails or returns a partial week", async () => {
  mocks.generateWeekly.mockResolvedValue([{ ...template, date: localDate() }]);
  const dialog = open();
  fireEvent.click(within(dialog).getByRole("button", { name: "Tạo thực đơn 7 ngày" }));
  await screen.findByText(/Chưa nhận được đủ các ngày/);
  expect(mocks.update).not.toHaveBeenCalled();
});
