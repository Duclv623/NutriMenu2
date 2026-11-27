import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { MealPlannerPage as MealPlanner } from "../features/meals/pages/MealPlannerPage";
import { MealChangeModal } from "../features/meals/components/MealChangeModal";
import { FoodDetail } from "../features/catalog/components/FoodDetail";
import { createDemoData } from "../data/seed";
import type { UserData } from "../types";

const mocks = vi.hoisted(() => ({
  preview: vi.fn(),
  apply: vi.fn(),
  update: vi.fn(),
}));
let workspace: UserData;
vi.mock("../store/UserDataProvider", () => ({
  useApp: () => ({ userData: workspace, update: mocks.update, toast: vi.fn() }),
}));
vi.mock("../features/meals/services/mealPlanService", async (load) => {
  const actual = await load<typeof import("../features/meals/services/mealPlanService")>();
  return {
    ...actual,
    API_MODE: "api",
    mealPlanService: {
      ...actual.mealPlanService,
      previewSwap: mocks.preview,
      swap: mocks.apply,
    },
  };
});
beforeEach(() => {
  vi.clearAllMocks();
  workspace = structuredClone(createDemoData());
  workspace.foods = workspace.foods.map((food) => ({
    ...food,
    recipeSource: `NIN_REAL_TEST_${food.id}`,
    solverEligible: true,
  }));
  const p = workspace.plans[0];
  workspace.plans = [
    {
      ...p,
      id: "77",
      date: "2026-11-22",
      dataMode: "api",
      solverStatus: "FEASIBLE",
      version: 4,
      items: [p.items[0]],
    },
  ];
  workspace.completedMeals = [];
  workspace.capabilities = {
    generate: true,
    swap: true,
    cookingTime: false,
    atomicVersions: true,
    favoriteRemoval: false,
  };
  workspace.foods.forEach((f) => {
    f.servingRules = { min: 0.5, max: 2, step: 0.5 };
  });
});
function planner() {
  render(
    <MemoryRouter initialEntries={["/thuc-don?date=2026-11-22&plan=77"]}>
      <MealPlanner />
    </MemoryRouter>,
  );
}
function openChange() {
  planner();
  fireEvent.click(screen.getByRole("button", { name: "Đổi món / khẩu phần" }));
  fireEvent.change(screen.getByLabelText("Khẩu phần"), {
    target: { value: "1.5" },
  });
}
describe("checked meal changes", () => {
  it("keeps the requested fish replacement without offering chicken or tomato dishes", () => {
    const plan = workspace.plans[0];
    const item = plan.items[0];
    const original = workspace.foods.find((f) => f.id === item.dishId)!;
    workspace.foods = [
      { ...original, name: "Ức gà kho gừng" },
      { ...original, id: "fish", name: "Cá thu nướng sả" },
      { ...original, id: "tomato", name: "Cà chua nhồi đậu" },
    ];
    render(
      <MealChangeModal
        plan={plan}
        item={item}
        onClose={vi.fn()}
        editor={{
          planId: plan.id,
          baseVersion: plan.version,
          itemIds: [item.id],
          instruction: "Đổi ức gà sang cá",
          preferredTerms: ["cá"],
        }}
      />,
    );
    expect(
      screen.getByRole("option", { name: "Cá thu nướng sả" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("option", { name: "Ức gà kho gừng" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("option", { name: "Cà chua nhồi đậu" }),
    ).not.toBeInTheDocument();
    expect(mocks.preview).not.toHaveBeenCalled();
    expect(mocks.apply).not.toHaveBeenCalled();
  });
  it("honors the chat preference for cá without excluding cà chua", () => {
    const plan = workspace.plans[0];
    const item = plan.items[0];
    const original = workspace.foods.find((f) => f.id === item.dishId)!;
    workspace.foods = [
      { ...original, name: "Cá thu", ingredientsComplete: true },
      {
        ...original,
        id: "tomato",
        name: "Cà chua nhồi đậu",
        ingredients: [{ id: "tomato", name: "Cà chua", grams: 100 }],
        ingredientsComplete: true,
      },
      {
        ...original,
        id: "mixed",
        name: "Chả hỗn hợp",
        ingredients: [{ id: "fish", name: "Cá, viên", grams: 100 }],
        ingredientsComplete: true,
      },
    ];
    render(
      <MealChangeModal
        plan={plan}
        item={item}
        onClose={vi.fn()}
        editor={{
          planId: plan.id,
          baseVersion: plan.version,
          itemIds: [item.id],
          instruction: "Không muốn ăn cá",
          excludedTerms: ["cá"],
        }}
      />,
    );
    expect(
      screen.getByRole("option", { name: "Cà chua nhồi đậu" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("option", { name: "Cá thu" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("option", { name: "Chả hỗn hợp" }),
    ).not.toBeInTheDocument();
  });
  it("offers catalog portions through the checked API limit in valid steps", async () => {
    const plan = workspace.plans[0];
    const item = plan.items[0];
    const food = workspace.foods.find((candidate) => candidate.id === item.dishId)!;
    food.servingRules = { min: 0.5, max: 13, step: 1 };
    mocks.preview.mockResolvedValue({
      ...plan, validationPassed: true, changeSignature: "wide-portion",
      items: [{ ...item, servingMultiplier: 12 }],
    });
    render(<MealChangeModal plan={plan} item={item} onClose={vi.fn()} />);
    const select = screen.getByLabelText("Khẩu phần");
    expect(select.querySelector('option[value="3.5"]')).toBeNull();
    expect(select.querySelector('option[value="12"]')).not.toBeNull();
    expect(select.querySelector('option[value="13"]')).toBeNull();
    fireEvent.change(select, { target: { value: "12" } });
    fireEvent.click(screen.getByRole("button", { name: "Xem trước thay đổi" }));
    await waitFor(() => expect(mocks.preview).toHaveBeenCalledWith(plan, item.id, item.dishId, 12));
  });

  it("requires a server preview and sends its signature when saving", async () => {
    const plan = workspace.plans[0];
    const checked = {
      ...plan,
      validationPassed: true,
      changeSignature: "server-signature",
      items: [{ ...plan.items[0], servingMultiplier: 1.5 }],
    };
    mocks.preview.mockResolvedValue(checked);
    mocks.apply.mockResolvedValue({ ...checked, version: 5 });
    openChange();
    expect(
      screen.queryByRole("button", { name: "Áp dụng thay đổi" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByLabelText("Khẩu phần").querySelector('option[value="0.75"]'),
    ).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "Xem trước thay đổi" }));
    await screen.findByRole("button", { name: "Áp dụng thay đổi" });
    expect(mocks.update).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Áp dụng thay đổi" }));
    await waitFor(() =>
      expect(mocks.apply).toHaveBeenCalledWith(
        plan,
        plan.items[0].id,
        plan.items[0].dishId,
        1.5,
        "server-signature",
      ),
    );
    await waitFor(() => expect(mocks.update).toHaveBeenCalledOnce());
    const updated = mocks.update.mock.calls[0][0](workspace);
    expect(updated.plans[0].version).toBe(5);
  });
  it("preserves the existing plan when the solver rejects a preview", async () => {
    mocks.preview.mockRejectedValue(new Error("Phương án không có nghiệm"));
    openChange();
    fireEvent.click(screen.getByRole("button", { name: "Xem trước thay đổi" }));
    await screen.findByText("Phương án không có nghiệm");
    expect(mocks.apply).not.toHaveBeenCalled();
    expect(mocks.update).not.toHaveBeenCalled();
  });
  it("does not keep the previous preview after changing portions", async () => {
    mocks.preview.mockResolvedValue({
      ...workspace.plans[0],
      validationPassed: true,
      changeSignature: "signature",
    });
    openChange();
    fireEvent.click(screen.getByRole("button", { name: "Xem trước thay đổi" }));
    await screen.findByRole("button", { name: "Áp dụng thay đổi" });
    fireEvent.change(screen.getByLabelText("Khẩu phần"), {
      target: { value: "0.5" },
    });
    expect(
      screen.queryByRole("button", { name: "Áp dụng thay đổi" }),
    ).not.toBeInTheDocument();
    expect(mocks.apply).not.toHaveBeenCalled();
  });
  it("explains why an old optimizer cannot enable swapping", () => {
    workspace.capabilities!.swap = false;
    planner();
    expect(
      screen.getByRole("button", { name: "Đổi món / khẩu phần" }),
    ).toBeDisabled();
    expect(
      screen.getByText(/Đổi món tạm thời chưa sẵn sàng/),
    ).toBeInTheDocument();
  });
  it("shows catalog instructions with their actual provenance", () => {
    const food = {
      ...workspace.foods[0],
      steps: ["Cân cà rốt: 45 g"],
      preparationGuideKind: "method_based",
    };
    render(<FoodDetail food={food} onClose={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: "Cách chuẩn bị" }));
    expect(screen.getByText("Cân cà rốt: 45 g")).toBeInTheDocument();
    expect(
      screen.getByText(/Chưa có công thức nấu hoàn chỉnh được kiểm chứng/),
    ).toBeInTheDocument();
  });
});

vi.mock("../lib/config", () => ({ API_MODE: "api", RAG_ENABLED: true }));
