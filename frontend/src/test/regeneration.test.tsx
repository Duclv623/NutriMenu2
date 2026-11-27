import { beforeEach, expect, it, vi } from "vitest";
import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { createDemoData } from "../data/seed";
import { MealPlannerPage as MealPlanner } from "../features/meals/pages/MealPlannerPage";

const mocks = vi.hoisted(() => ({
  generate: vi.fn(),
  update: vi.fn(),
  toast: vi.fn(),
}));
let workspace = createDemoData();
vi.mock("../store/UserDataProvider", () => ({
  useApp: () => ({ userData: workspace, update: mocks.update, toast: mocks.toast }),
}));
vi.mock("../features/meals/services/mealPlanService", async (load) => ({
  ...(await load<typeof import("../features/meals/services/mealPlanService")>()),
  API_MODE: "api",
  mealPlanService: { generate: mocks.generate },
}));

beforeEach(() => {
  vi.clearAllMocks();
  workspace = structuredClone(createDemoData());
  workspace.foods.forEach((food) => {
    food.recipeSource = `NIN_REAL_TEST_${food.id}`;
    food.solverEligible = true;
  });
  workspace.capabilities = {
    generate: true,
    regenerate: true,
    swap: true,
    favoriteRemoval: false,
    cookingTime: false,
    atomicVersions: true,
  };
  const plan = workspace.plans[0];
  plan.id = "7";
  plan.version = 4;
  plan.planningMode = "MEDICAL";
  plan.dataMode = "api";
  plan.solverStatus = "FEASIBLE";
  plan.items = [{ ...plan.items[0], locked: false }];
  workspace.completedMeals = [];
});
function open() {
  render(
    <MemoryRouter>
      <MealPlanner />
    </MemoryRouter>,
  );
  fireEvent.click(screen.getByRole("button", { name: "Tạo lại thực đơn" }));
  return screen.getByRole("dialog");
}

it("requires explicit confirmation and forwards the existing id/version and chosen mode", async () => {
  const previous = workspace.plans[0];
  workspace.foods.find((f) => f.id === previous.items[0].dishId)!.recipeSource =
    "QA_OLD";
  const replacementFood = workspace.foods.find((f) =>
    f.recipeSource?.startsWith("NIN_REAL_"),
  )!;
  const next = {
    ...previous,
    version: 5,
    planningMode: "GENERAL_REFERENCE",
    items: [
      { ...previous.items[0], id: "new-item", dishId: replacementFood.id },
    ],
  };
  mocks.generate.mockResolvedValue(next);
  const dialog = open();
  const submit = within(dialog).getByRole("button", {
    name: "Xác nhận tạo lại",
  });
  expect(submit).toBeDisabled();
  expect(
    within(dialog).getByRole("combobox", { name: "Chế độ thực đơn" }),
  ).toHaveValue("MEDICAL");
  fireEvent.click(submit);
  expect(mocks.generate).not.toHaveBeenCalled();
  fireEvent.change(
    within(dialog).getByRole("combobox", { name: "Chế độ thực đơn" }),
    { target: { value: "GENERAL_REFERENCE" } },
  );
  fireEvent.click(
    within(dialog).getByRole("checkbox", {
      name: "Tôi xác nhận thay thế thực đơn hiện tại",
    }),
  );
  fireEvent.click(submit);
  await waitFor(() =>
    expect(mocks.generate).toHaveBeenCalledWith(
      expect.objectContaining({
        replacePlanId: "7",
        baseVersion: 4,
        replacementConfirmed: true,
        planningMode: "GENERAL_REFERENCE",
      }),
      workspace.profile.version,
    ),
  );
  await waitFor(() => expect(mocks.update).toHaveBeenCalled());
  const updated = mocks.update.mock.calls[0][0](workspace);
  expect(updated.plans[0]).toEqual(next);
  expect(previous.version).toBe(4);
});

it("leaves the current plan untouched when generation fails", async () => {
  const before = JSON.stringify(workspace.plans);
  mocks.generate.mockRejectedValue(
    new Error("Không có phương án phù hợp; giữ thực đơn cũ."),
  );
  const dialog = open();
  fireEvent.click(
    within(dialog).getByRole("checkbox", {
      name: "Tôi xác nhận thay thế thực đơn hiện tại",
    }),
  );
  fireEvent.click(
    within(dialog).getByRole("button", { name: "Xác nhận tạo lại" }),
  );
  await within(dialog).findByText(
    "Không có phương án phù hợp; giữ thực đơn cũ.",
  );
  expect(mocks.update).not.toHaveBeenCalled();
  expect(JSON.stringify(workspace.plans)).toBe(before);
});

it.each(["locked", "completed"])(
  "does not erase %s meal state during regeneration",
  (state) => {
    const plan = workspace.plans[0];
    if (state === "locked") plan.items[0].locked = true;
    else workspace.completedMeals = [`${plan.id}:${plan.items[0].id}`];
    const dialog = open();
    expect(
      within(dialog).getByRole("button", { name: "Xác nhận tạo lại" }),
    ).toBeDisabled();
    expect(
      within(dialog).getByText(/Hãy bỏ giữ món hoặc chọn ngày khác/),
    ).toBeVisible();
    expect(mocks.generate).not.toHaveBeenCalled();
  },
);

it("does not submit a new day until the user explicitly selects its mode", async () => {
  workspace.plans = [];
  mocks.generate.mockRejectedValue(new Error("Chưa đủ món phù hợp."));
  render(
    <MemoryRouter>
      <MealPlanner />
    </MemoryRouter>,
  );
  fireEvent.click(screen.getByRole("button", { name: "Tạo thực đơn" }));
  const dialog = screen.getByRole("dialog");
  const mode = within(dialog).getByRole("combobox", {
    name: "Chế độ thực đơn",
  });
  expect(mode).toHaveValue("");
  expect(mode).toBeRequired();
  fireEvent.submit(dialog.querySelector("form")!);
  expect(mocks.generate).not.toHaveBeenCalled();
  expect(
    within(dialog).getByText("Vui lòng chọn chế độ thực đơn trước khi tạo."),
  ).toBeVisible();
  fireEvent.change(mode, { target: { value: "MEDICAL" } });
  fireEvent.click(
    within(dialog).getByRole("button", { name: "Tạo bằng bộ tối ưu" }),
  );
  await waitFor(() =>
    expect(mocks.generate).toHaveBeenCalledWith(
      expect.objectContaining({ planningMode: "MEDICAL" }),
      workspace.profile.version,
    ),
  );
  expect(mode).toHaveValue("MEDICAL");
});

vi.mock("../lib/config", () => ({ API_MODE: "api", RAG_ENABLED: true }));
