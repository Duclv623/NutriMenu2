import type { CatalogNutrients, Plan, UserData } from "./types";
import { addDays, emptyNutrition, sumNutrition } from "./utils";

export type DaySummary = {
  date: string;
  plan?: Plan;
  target: number | null;
  planned: CatalogNutrients | null;
  recorded: CatalogNutrients | null;
  recordedCount: number;
  itemCount: number;
};
export type Insight = {
  id: string;
  tone: "amber" | "green" | "neutral";
  title: string;
  description: string;
  action: string;
  to: string;
};

// A calendar day has one active plan. Never add saved revisions together.
export function dailySummaries(
  w: UserData,
  today: string,
  days = 7,
): DaySummary[] {
  const plans = new Map<string, Plan>();
  for (const plan of w.plans) {
    if (
      !plan.items.length ||
      !["OPTIMAL", "FEASIBLE", "NOT_RUN"].includes(plan.solverStatus)
    )
      continue;
    const existing = plans.get(plan.date);
    if (
      !existing ||
      (plan.createdAt || "").localeCompare(existing.createdAt || "") > 0 ||
      (plan.createdAt === existing.createdAt && plan.version > existing.version)
    ) {
      plans.set(plan.date, plan);
    }
  }
  const completed = new Set(w.completedMeals);
  return Array.from({ length: days }, (_, index) => {
    const date = addDays(today, index - days + 1);
    const plan = plans.get(date);
    const recorded =
      plan?.items.filter((item) => completed.has(`${plan.id}:${item.id}`)) ||
      [];
    return {
      date,
      plan,
      target:
        plan && Number.isFinite(plan.target) && plan.target > 0
          ? plan.target
          : null,
      planned: plan ? sumNutrition(plan.items, w.foods) : null,
      recorded: recorded.length ? sumNutrition(recorded, w.foods) : null,
      recordedCount: recorded.length,
      itemCount: plan?.items.length || 0,
    };
  });
}

export function dashboardInsights(
  w: UserData,
  days: DaySummary[],
  today: string,
): Insight[] {
  const insights: Insight[] = [];
  const current = days.find((day) => day.date === today);
  const menu = current?.plan;
  const menuUrl = `/thuc-don?date=${today}${menu ? `&plan=${encodeURIComponent(menu.id)}` : ""}`;
  if (!w.profile.confirmed)
    insights.push({
      id: "profile",
      tone: "amber",
      title: "Hoàn thiện hồ sơ dinh dưỡng",
      description:
        "Xác nhận thông tin cá nhân, dị ứng và món không ăn để áp dụng khi tạo thực đơn.",
      action: "Cập nhật hồ sơ",
      to: "/ho-so",
    });
  if (!menu)
    insights.push({
      id: "no-plan",
      tone: "neutral",
      title: "Hôm nay chưa có thực đơn",
      description:
        "Lên kế hoạch để xem dinh dưỡng dự kiến và ghi nhận từng món đã ăn.",
      action: "Lên thực đơn",
      to: menuUrl,
    });
  else if (menu.profileVersion !== w.profile.version)
    insights.push({
      id: "stale",
      tone: "amber",
      title: "Hồ sơ đã thay đổi",
      description:
        "Thực đơn hôm nay được tạo trước lần cập nhật hồ sơ gần nhất. Hãy xem lại trước khi tiếp tục ghi nhận.",
      action: "Xem lại thực đơn",
      to: menuUrl,
    });
  else if (!current.recordedCount)
    insights.push({
      id: "no-records",
      tone: "neutral",
      title: "Bạn chưa ghi nhận món đã ăn hôm nay",
      description:
        "Đánh dấu sau khi ăn để cập nhật biểu đồ. Chưa ghi nhận không có nghĩa là bạn chưa ăn.",
      action: "Ghi nhận đã ăn",
      to: menuUrl,
    });
  else if (current.recordedCount < current.itemCount)
    insights.push({
      id: "partial",
      tone: "neutral",
      title: "Nhật ký hôm nay đang được cập nhật",
      description: `Đã ghi nhận ${current.recordedCount}/${current.itemCount} món trong thực đơn. Tổng dinh dưỡng chỉ tính những món bạn đã xác nhận.`,
      action: "Cập nhật nhật ký",
      to: menuUrl,
    });
  if (
    current?.recorded &&
    current.target &&
    current.recorded.energyKcal !== null &&
    current.recorded.energyKcal > current.target
  ) {
    insights.push({
      id: "above-target",
      tone: "amber",
      title: "Năng lượng đã ghi nhận vượt mục tiêu hôm nay",
      description: `Tổng từ các khẩu phần đã xác nhận cao hơn mục tiêu ${Math.round(current.recorded.energyKcal - current.target).toLocaleString("vi-VN")} kcal. Bạn có thể kiểm tra lại món và khẩu phần đã ghi nhận.`,
      action: "Xem các khẩu phần",
      to: menuUrl,
    });
  }
  if (current?.recordedCount && current.recordedCount === current.itemCount)
    insights.push({
      id: "all-recorded",
      tone: "green",
      title: "Đã ghi nhận đủ các món trong thực đơn hôm nay",
      description:
        "Biểu đồ đã cập nhật theo khẩu phần trong thực đơn. Các món ăn thêm bên ngoài chưa nằm trong tổng này.",
      action: "Xem nhật ký",
      to: menuUrl,
    });
  const unknown =
    current?.planned &&
    Object.entries(current.planned)
      .filter(([, value]) => value === null)
      .map(
        ([key]) =>
          ({
            energyKcal: "năng lượng",
            netCarbG: "carbohydrate",
            proteinG: "đạm",
            fatG: "chất béo",
            fiberG: "chất xơ",
            sodiumMg: "natri",
          })[key],
      );
  if (unknown?.length)
    insights.push({
      id: "unknown",
      tone: "amber",
      title: "Một số chỉ số chưa đủ dữ liệu",
      description: `Chưa thể tổng hợp ${unknown.join(", ")}. Những giá trị này được để trống, không tính bằng 0.`,
      action: "Xem chi tiết món",
      to: menuUrl,
    });
  const repeats = new Map<string, Set<string>>();
  for (const day of days.filter((day) => day.date >= addDays(today, -6))) {
    for (const item of day.plan?.items || []) {
      if (!w.completedMeals.includes(`${day.plan!.id}:${item.id}`)) continue;
      const dates = repeats.get(item.dishId) || new Set<string>();
      dates.add(day.date);
      repeats.set(item.dishId, dates);
    }
  }
  const repeated = [...repeats]
    .sort((a, b) => b[1].size - a[1].size)
    .find(([, dates]) => dates.size >= 3);
  const food = repeated && w.foods.find((food) => food.id === repeated[0]);
  if (food && repeated)
    insights.push({
      id: "repeat",
      tone: "neutral",
      title: "Gợi ý thêm sự đa dạng",
      description: `Bạn đã ghi nhận ${food.name} trong ${repeated[1].size} ngày của 7 ngày gần nhất. Có thể chọn món khác cho thực đơn tiếp theo nếu muốn đổi vị.`,
      action: "Khám phá món ăn",
      to: "/mon-an",
    });
  if (!insights.length)
    insights.push({
      id: "start",
      tone: "neutral",
      title: "Theo dõi từ những bữa ăn hằng ngày",
      description:
        "Ghi nhận món đã ăn để xem thay đổi theo thời gian và nhận gợi ý phù hợp với nhật ký.",
      action: "Xem thực đơn",
      to: menuUrl,
    });
  return insights;
}

export function recordedAverage(days: DaySummary[]) {
  const available = days.filter(
    (day) =>
      day.recorded?.energyKcal !== null && day.recorded?.energyKcal !== undefined,
  );
  return available.length
    ? available.reduce((sum, day) => sum + day.recorded!.energyKcal!, 0) /
        available.length
    : null;
}

export const noRecordedNutrition = (): CatalogNutrients =>
  Object.fromEntries(
    Object.keys(emptyNutrition()).map((key) => [key, null]),
  ) as CatalogNutrients;
