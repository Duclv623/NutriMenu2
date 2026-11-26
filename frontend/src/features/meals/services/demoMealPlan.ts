import type { GenerateInput, Plan, UserData } from "../../../types";
import { allowedFood, assertEligible, uid } from "../../../utils";
export function generateDemo(input: GenerateInput, state: UserData): Plan {
  assertEligible(state.profile);
  if (
    !Number.isFinite(input.target) ||
    input.target < 1000 ||
    input.target > 4000
  )
    throw new Error("Mục tiêu demo cần nằm trong khoảng 1.000–4.000 kcal.");
  const meals = input.snacks
    ? (["breakfast", "lunch", "dinner", "snack"] as const)
    : (["breakfast", "lunch", "dinner"] as const);
  const current = state.plans.find((p) => p.date === input.date);
  const used = new Set<string>();
  const items = meals.map((meal) => {
    const locked = current?.items.find((i) => i.meal === meal && i.locked);
    if (locked) {
      const food = state.foods.find((f) => f.id === locked.foodId);
      if (!food || !allowedFood(food, state.profile))
        throw new Error(
          "Món đang giữ không còn phù hợp hồ sơ. Hãy bỏ giữ món rồi thử lại.",
        );
      used.add(locked.foodId);
      return locked;
    }
    const candidates = state.foods.filter(
      (f) =>
        f.meals.includes(meal) &&
        allowedFood(f, state.profile) &&
        f.minutes !== null &&
        f.minutes <= input.maxMinutes &&
        (!input.excludeRepeat || !used.has(f.id)),
    );
    if (!candidates.length)
      throw new Error(
        `Không có món cho ${meal === "breakfast" ? "bữa sáng" : meal === "lunch" ? "bữa trưa" : meal === "dinner" ? "bữa tối" : "bữa phụ"} đáp ứng bộ lọc. Hãy tăng thời gian nấu hoặc bổ sung danh mục; dị ứng luôn được giữ.`,
      );
    const food = candidates[Math.floor(Math.random() * candidates.length)];
    used.add(food.id);
    const mealTarget = input.snacks
      ? meal === "lunch"
        ? input.target * 0.35
        : meal === "breakfast"
          ? input.target * 0.28
          : meal === "dinner"
            ? input.target * 0.27
            : input.target * 0.1
      : meal === "lunch"
        ? input.target * 0.4
        : meal === "breakfast"
          ? input.target * 0.32
          : input.target * 0.28;
    const rawServings =
      Number.isFinite(food.nutrition.energy) && food.nutrition.energy > 0
        ? mealTarget / food.nutrition.energy
        : 1;
    const servings = Math.max(
      0.5,
      Math.min(3.0, Math.round(rawServings * 2) / 2),
    );
    return { id: uid(), foodId: food.id, meal, servings, locked: false };
  });
  return {
    id: uid(),
    date: input.date,
    profileVersion: state.profile.version,
    version: 1,
    items,
    solverStatus: "NOT_RUN",
    dataMode: "demo",
    target: input.target,
    createdAt: new Date().toISOString(),
    notes: [
      "Mẫu được chọn theo bộ lọc giao diện; chưa tối ưu theo năng lượng hoặc chạy CP-SAT.",
    ],
  };
}
