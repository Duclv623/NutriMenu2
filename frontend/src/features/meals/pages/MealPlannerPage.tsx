import { useRef, useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router-dom";
import {
  ArrowRight,
  Check,
  ChevronLeft,
  ChevronRight,
  Clock3,
  Download,
  LockKeyhole,
  Plus,
  RefreshCw,
  ShoppingBasket,
  UnlockKeyhole,
} from "lucide-react";
import { useApp } from "../../../store/UserDataProvider";
import type { Food, GenerateInput, MealType, Plan, PlanItem } from "../../../types";
import {
  addDays,
  assertEligible,
  assertNewSourceSelection,
  dateLabel,
  downloadFile,
  localDate,
  number,
  ingredientAmount,
  planCarbohydrateLabel,
  planUsesHistoricalData,
  planningReady,
  scaleAmount,
  servingLabel,
  sumNutrition,
} from "../../../utils";
import { API_MODE } from "../../../lib/config";
import { mealPlanService } from "../services/mealPlanService";
import { generateDemo } from "../services/demoMealPlan";
import { MEAL_PLANS, callService } from "../../../lib/serviceActions";
import { MEALS } from "../../../data/seed";
import {
  Alert,
  Badge,
  Button,
  Checkbox,
  Empty,
  FoodMark,
  FoodSource,
  IconButton,
  Modal,
  Nutrition,
  PageTitle,
} from "../../../components/common/ui";
import { FoodDetail } from "../../catalog/components/FoodDetail";
import { MealChangeModal } from "../components/MealChangeModal";
import {
  OptimizerGenerateDialog,
  PlanToolDialog,
  type PlanTool,
} from "../components/PlanTools";
const solverLabels = {
  OPTIMAL: "Tối ưu",
  FEASIBLE: "Có phương án",
  INFEASIBLE: "Chưa có thực đơn phù hợp",
  UNKNOWN: "Chưa tìm được phương án",
  MODEL_INVALID: "Cấu hình không hợp lệ",
  NOT_RUN: "Chưa kiểm tra dinh dưỡng",
};
export function MealPlannerPage() {
  const { userData: w, update, reload, toast } = useApp();
  const [params, setParams] = useSearchParams();
  const validDate =
    /^\d{4}-\d{2}-\d{2}$/.test(params.get("date") || "") &&
    !Number.isNaN(new Date(params.get("date") + "T12:00:00").valueOf());
  const date = validDate ? params.get("date")! : localDate();
  const plan =
    (params.get("plan")
      ? w.plans.find((p) => p.id === params.get("plan") && p.date === date)
      : undefined) || w.plans.find((p) => p.date === date);
  const [generateOpen, setGenerateOpen] = useState(false);
  const [advancedGenerateOpen, setAdvancedGenerateOpen] = useState(false);
  const [tool, setTool] = useState<PlanTool | null>(null);
  const [swap, setSwap] = useState<PlanItem | null>(null);
  const [detail, setDetail] = useState<Food | null>(null);
  const [busy, setBusy] = useState("");
  const setDate = (value: string) => {
    setParams({ date: value });
  };
  const totals = plan ? sumNutrition(plan.items, w.foods) : null;
  const historical = plan && planUsesHistoricalData(plan, w.foods);
  const stale = plan && plan.profileVersion !== w.profile.version;
  const hasSkipped = !!plan?.skippedItems?.length;
  const rejected =
    plan &&
    ["INFEASIBLE", "UNKNOWN", "MODEL_INVALID"].includes(plan.solverStatus);
  async function lock(item: PlanItem) {
    if (!plan) return;
    setBusy(item.id);
    try {
      let next = {
        ...plan,
        version: plan.version + 1,
        items: plan.items.map((i) =>
          i.id === item.id ? { ...i, locked: !i.locked } : i,
        ),
      };
      if (API_MODE === "api")
        next = await mealPlanService.lock(plan, item.id, !item.locked);
      update((s) => ({
        ...s,
        plans: s.plans.map((p) => (p.id === next.id ? next : p)),
      }));
    } catch (e) {
      toast((e as Error).message);
    } finally {
      setBusy("");
    }
  }
  async function complete(item: PlanItem) {
    if (!plan || date > localDate() || busy) return;
    const key = `${plan.id}:${item.id}`;
    const selected = !w.completedMeals.includes(key);
    if (API_MODE === "api") {
      await setItemStatus(item, selected ? "COMPLETED" : "PLANNED");
      return;
    }
    setBusy(item.id);
    try {
      update((s) => ({
        ...s,
        completedMeals: selected
          ? [...new Set([...s.completedMeals, key])]
          : s.completedMeals.filter((k) => k !== key),
      }));
    } catch (e) {
      toast((e as Error).message);
    } finally {
      setBusy("");
    }
  }
  async function setItemStatus(
    item: PlanItem,
    status: "PLANNED" | "COMPLETED" | "SKIPPED",
  ) {
    if (!plan) return;
    setBusy(item.id);
    try {
      await callService(
        "optimizer",
        "PATCH",
        MEAL_PLANS + "/{id}/items/{itemId}/status",
        {
          ids: { id: plan.id, itemId: item.id },
          body: { status },
        },
      );
      try {
        await reload(true);
      } catch {
        toast(
          "Đã cập nhật trạng thái. Tải lại trang để xem thực đơn mới nhất.",
        );
      }
    } catch (error) {
      toast((error as Error).message);
    } finally {
      setBusy("");
    }
  }
  function exportPlan() {
    if (!plan || !totals) return;
    const lines = [
      `NutriGuide — Thực đơn ${dateLabel(date)}`,
      `Chế độ: ${plan.planningMode === "GENERAL_REFERENCE" ? "Phổ thông" : "Bệnh lý"}`,
      `Trạng thái: ${solverLabels[plan.solverStatus]}`,
      ...plan.items.map((i) => {
        const f = w.foods.find((f) => f.id === i.dishId)!;
        return `\n${MEALS[i.meal].name}: ${f.name}\nKhẩu phần: ${number(i.servingMultiplier, 2)} phần · ${servingLabel(f, i.servingMultiplier)}\n${f.ingredients.map((x) => `${x.name}: ${ingredientAmount(x, i.servingMultiplier)}`).join("\n")}`;
      }),
      `\nTổng: ${number(totals.energyKcal)} kcal; ${planCarbohydrateLabel(plan.items, w.foods)} ${number(totals.netCarbG, 1)} g; Đạm ${number(totals.proteinG, 1)} g; Béo ${number(totals.fatG, 1)} g; Xơ ${number(totals.fiberG, 1)} g; Natri ${number(totals.sodiumMg)} mg.`,
      ...plan.notes,
    ];
    downloadFile(`thuc-don-${date}.txt`, lines.join("\n"));
  }
  return (
    <div className="stack-lg">
      <PageTitle
        title="Thực đơn của tôi"
        actions={
          <>
            <Button
              variant="secondary"
              onClick={exportPlan}
              disabled={!plan || !!rejected}
            >
              <Download size={16} />
              Xuất thực đơn
            </Button>
            {API_MODE === "api" && (
              <Button
                variant="secondary"
                onClick={() => setTool({ kind: "feasibility" })}
              >
                Kiểm tra khả năng tạo
              </Button>
            )}
            {API_MODE === "api" && <Button variant="secondary" onClick={() => setAdvancedGenerateOpen(true)}>Tạo theo hồ sơ / tùy chỉnh</Button>}
            <Button onClick={() => setGenerateOpen(true)}>
              <Plus size={17} />
              {plan ? "Tạo lại thực đơn" : "Tạo thực đơn"}
            </Button>
          </>
        }
      />
      <div className="calendar-strip panel">
        <IconButton
          label="Tuần trước"
          onClick={() => setDate(addDays(date, -7))}
        >
          <ChevronLeft size={20} />
        </IconButton>
        <div className="calendar-days">
          {Array.from({ length: 7 }, (_, i) => addDays(date, i - 3)).map(
            (d) => (
              <button
                key={d}
                className={d === date ? "active" : ""}
                aria-pressed={d === date}
                aria-label={dateLabel(d)}
                onClick={() => setDate(d)}
              >
                <span>{dateLabel(d, { weekday: "short" })}</span>
                <strong>{new Date(d + "T12:00:00").getDate()}</strong>
                <i
                  className={
                    w.plans.some((p) => p.date === d) ? "has-plan" : ""
                  }
                />
              </button>
            ),
          )}
        </div>
        <IconButton label="Tuần sau" onClick={() => setDate(addDays(date, 7))}>
          <ChevronRight size={20} />
        </IconButton>
      </div>
      <div className="date-heading">
        <div>
          <h2>{dateLabel(date)}</h2>
          {plan && (
            <Badge
              tone={
                plan.dataMode === "demo" ? "amber" : rejected ? "red" : "green"
              }
            >
              {solverLabels[plan.solverStatus]}
            </Badge>
          )}
        </div>
        <div className="actions">
          <input
            type="date"
            aria-label="Chọn ngày thực đơn"
            value={date}
            onChange={(e) => {
              if (e.target.value) setDate(e.target.value);
            }}
          />
          <Button variant="ghost" onClick={() => setDate(localDate())}>
            Hôm nay
          </Button>
        </div>
      </div>
      {plan && (
        <div className="panel fineprint">
          <strong>
            {plan.planningMode === "GENERAL_REFERENCE"
              ? "Phổ thông"
              : "Chế độ bệnh lý"}
          </strong>
          {plan.planningMode === "GENERAL_REFERENCE"
            ? ": Dựa trên mục tiêu năng lượng bạn chọn. Dinh dưỡng được ước tính theo khẩu phần; chế độ này không đánh giá GI/GL và không dùng để điều trị bệnh."
            : ": Giữ giới hạn GI, GL và quy tắc bệnh lý của bộ tối ưu. Kết quả chưa thay thế tư vấn của chuyên gia."}
        </div>
      )}
      {historical && (
        <Alert>
          <strong>Thực đơn dùng dữ liệu cũ</strong>. Thực đơn này được giữ để
          xem lại. Bạn có thể chọn Tạo lại thực đơn và xác nhận thay thế bằng dữ
          liệu NIN mới. Bản cũ được lưu khi máy chủ tạo lại thành công.
        </Alert>
      )}
      {stale && (
        <Alert error>
          Hồ sơ đã đổi từ lúc tạo thực đơn. Chọn một ngày trống để tạo thực đơn
          theo hồ sơ mới.
        </Alert>
      )}
      {plan && w.capabilities?.swap === false && (
        <Alert>
          Đổi món tạm thời chưa sẵn sàng. Bạn có thể xem thực đơn và thử lại
          sau.
        </Alert>
      )}
      {!plan ? (
        <Empty
          title="Chưa có thực đơn"
          action={
            <Button onClick={() => setGenerateOpen(true)}>
              <Plus size={17} />
              Tạo thực đơn cho ngày này
            </Button>
          }
        >
          Bạn chưa có thực đơn cho ngày đã chọn.
        </Empty>
      ) : rejected ? (
        <Alert error>
          {solverLabels[plan.solverStatus]}. {plan.notes.join(" ")} Hệ thống giữ
          nguyên các giới hạn bắt buộc. Bạn có thể thay đổi sở thích và thử lại.
        </Alert>
      ) : (
        <>
          <div className="planner-layout">
            <div className="stack">
              {(["breakfast", "lunch", "dinner", "snack"] as MealType[]).map(
                (meal) => {
                  const items = plan.items.filter((i) => i.meal === meal);
                  if (!items.length) return null;
                  return (
                    <section className="panel meal-panel" key={meal}>
                      <div className="meal-panel-heading">
                        <div>
                          <div>
                            <h3>{MEALS[meal].name}</h3>
                            {API_MODE === "demo" && (
                              <span>{MEALS[meal].time}</span>
                            )}
                          </div>
                        </div>
                        <strong>
                          {number(sumNutrition(items, w.foods).energyKcal)}{" "}
                          <small>kcal</small>
                        </strong>
                      </div>
                      {items.map((item) => {
                        const food = w.foods.find((f) => f.id === item.dishId);
                        if (!food) return <Alert key={item.id} error>Chưa tải được dữ liệu {item.name || "món trong thực đơn"}.</Alert>;
                        const completed = w.completedMeals.includes(
                          `${plan.id}:${item.id}`,
                        );
                        return (
                          <div key={item.id}>
                            <div className="planned-food">
                              {food && <FoodMark food={food} />}
                              <div className="planned-food-copy">
                                <button
                                  className="food-name"
                                  onClick={() => setDetail(food)}
                                >
                                  {food.name}
                                </button>
                                <FoodSource food={food} />
                                <p>
                                  {number(item.servingMultiplier, 2)} khẩu phần ·{" "}
                                  {servingLabel(food, item.servingMultiplier)}{" "}
                                  <span>·</span>
                                  <Clock3 size={13} />
                                  {food.minutes === null
                                    ? "Chưa có thời gian"
                                    : `${food.minutes} phút`}
                                </p>
                                <div className="mini-macros">
                                  <span>
                                    {planCarbohydrateLabel([item], w.foods)}{" "}
                                    <strong>
                                      {number(
                                        item.nutrition?.netCarbG ??
                                          scaleAmount(
                                            food.nutrition.netCarbG,
                                            item.servingMultiplier,
                                          ),
                                        1,
                                      )}{" "}
                                      g
                                    </strong>
                                  </span>
                                  <span>
                                    Đạm{" "}
                                    <strong>
                                      {number(
                                        item.nutrition?.proteinG ??
                                          scaleAmount(
                                            food.nutrition.proteinG,
                                            item.servingMultiplier,
                                          ),
                                        1,
                                      )}{" "}
                                      g
                                    </strong>
                                  </span>
                                  <span>
                                    Béo{" "}
                                    <strong>
                                      {number(
                                        item.nutrition?.fatG ??
                                          scaleAmount(
                                            food.nutrition.fatG,
                                            item.servingMultiplier,
                                          ),
                                        1,
                                      )}{" "}
                                      g
                                    </strong>
                                  </span>
                                </div>
                              </div>
                            </div>
                            <div className="planned-actions">
                              <Button
                                variant="ghost"
                                disabled={
                                  !!stale ||
                                  !!historical ||
                                  !planningReady(
                                    food,
                                    API_MODE === "demo",
                                    plan.planningMode,
                                  ) ||
                                  !!busy ||
                                  !food ||
                                  hasSkipped ||
                                  item.locked ||
                                  w.completedMeals.includes(
                                    `${plan.id}:${item.id}`,
                                  ) ||
                                  w.capabilities?.swap === false
                                }
                                onClick={() => setSwap(item)}
                              >
                                <RefreshCw size={14} />
                                Đổi món / khẩu phần
                              </Button>
                              <div className="actions">
                                <IconButton
                                  aria-pressed={item.locked}
                                  label={
                                    item.locked
                                      ? `Bỏ giữ ${food?.name ?? item.name ?? "món"}`
                                      : `Giữ ${food?.name ?? item.name ?? "món"}`
                                  }
                                  disabled={
                                    !!busy || (!!historical && !item.locked)
                                  }
                                  onClick={() => void lock(item)}
                                >
                                  {item.locked ? (
                                    <LockKeyhole size={16} />
                                  ) : (
                                    <UnlockKeyhole size={16} />
                                  )}
                                </IconButton>
                                <Button
                                  aria-pressed={completed}
                                  variant={completed ? "primary" : "secondary"}
                                  disabled={
                                    !!stale || !!busy || date > localDate()
                                  }
                                  onClick={() => void complete(item)}
                                >
                                  <Check size={14} />
                                  {completed ? "Đã ăn" : "Ghi nhận đã ăn"}
                                </Button>
                              </div>
                            </div>
                            {API_MODE === "api" && (
                              <details className="workflow-details planned-more">
                                <summary>Tùy chọn món</summary>
                                <div className="actions">
                                  <Button
                                    variant="secondary"
                                    disabled={
                                      !!busy ||
                                      !!stale ||
                                      !!historical ||
                                      item.locked ||
                                      completed ||
                                      hasSkipped ||
                                      !food
                                    }
                                    onClick={() =>
                                      setTool({ kind: "swap", item })
                                    }
                                  >
                                    Tự tìm món thay thế
                                  </Button>
                                  <Button
                                    variant="secondary"
                                    disabled={
                                      !!busy ||
                                      !!stale ||
                                      !!historical ||
                                      item.locked ||
                                      completed ||
                                      hasSkipped ||
                                      !food
                                    }
                                    onClick={() =>
                                      setTool({ kind: "replace", item })
                                    }
                                  >
                                    Thay món trực tiếp
                                  </Button>
                                  <Button
                                    variant="ghost"
                                    disabled={
                                      !!busy || item.locked || completed
                                    }
                                    onClick={() =>
                                      void setItemStatus(item, "SKIPPED")
                                    }
                                  >
                                    Bỏ qua món
                                  </Button>
                                </div>
                              </details>
                            )}
                          </div>
                        );
                      })}
                    </section>
                  );
                },
              )}
              {!!plan.skippedItems?.length && (
                <section className="panel workflow-panel stack">
                  <h3>Món đã bỏ qua</h3>
                  <p className="fineprint">
                    Khôi phục các món đã bỏ qua trước khi đổi món hoặc khẩu
                    phần.
                  </p>
                  {plan.skippedItems.map((item) => (
                    <div key={item.id} className="workflow-result-row">
                      <span>
                        {item.name ||
                          w.foods.find((food) => food.id === item.dishId)
                            ?.name ||
                          "Món đã bỏ"}{" "}
                        · {MEALS[item.meal].name}
                      </span>
                      <Button
                        variant="secondary"
                        disabled={!!busy}
                        onClick={() => void setItemStatus(item, "PLANNED")}
                      >
                        Khôi phục
                      </Button>
                    </div>
                  ))}
                </section>
              )}
            </div>
            <aside className="stack planner-summary">
              <section className="panel">
                <span className="eyebrow">TỔNG DINH DƯỠNG</span>
                <h2>Cả ngày của bạn</h2>
                {totals && <Nutrition value={totals} carbLabel={planCarbohydrateLabel(plan?.items || [], w.foods)} compact />}
                <div className="summary-target">
                  <span>Mục tiêu năng lượng</span>
                  <strong>{number(plan.target)} kcal</strong>
                </div>
                <p className="fineprint">
                  {plan.dataMode === "demo"
                    ? "Số liệu tính từ dữ liệu mẫu. Chưa kiểm tra các ràng buộc dinh dưỡng cá nhân."
                    : "Dinh dưỡng được ước tính theo khẩu phần từng món trong thực đơn."}
                </p>
                {API_MODE === "api" && (
                  <div className="actions workflow-plan-actions">
                    <Button
                      variant="secondary"
                      onClick={() => setTool({ kind: "nutrition" })}
                    >
                      Chi tiết dinh dưỡng
                    </Button>
                    <Button
                      variant="secondary"
                      disabled={!!stale || !!busy || !!historical || hasSkipped}
                      onClick={() => setTool({ kind: "rebalance" })}
                    >
                      Cân bằng các bữa còn lại
                    </Button>
                    <Button
                      variant="secondary"
                      onClick={() => setTool({ kind: "rate" })}
                    >
                      {plan.rating
                        ? `Sửa đánh giá (${plan.rating}/5)`
                        : "Đánh giá thực đơn"}
                    </Button>
                  </div>
                )}
              </section>
              <Link
                className="panel shopping-shortcut"
                to={`/mua-sam?plan=${plan.id}`}
              >
                <ShoppingBasket size={24} />
                <h3>Danh sách mua sắm</h3>
                <span>
                  Xem danh sách <ArrowRight size={16} />
                </span>
              </Link>
              <Link
                to={`/tro-ly?plan=${encodeURIComponent(plan.id)}&date=${plan.date}`}
                className="text-link"
              >
                Hỏi trợ lý về thực đơn <ArrowRight size={16} />
              </Link>
            </aside>
          </div>
          {plan.notes.map((note) => (
            <Alert key={note}>{note}</Alert>
          ))}
        </>
      )}
      {generateOpen && (
        <GenerateModal date={date} existingPlan={plan}
          onClose={() => setGenerateOpen(false)}
          onGenerated={(next) => setParams({ date: next.date, plan: next.id })} />
      )}
      {advancedGenerateOpen && <OptimizerGenerateDialog date={date} onClose={() => setAdvancedGenerateOpen(false)} />}
      {tool && <PlanToolDialog tool={tool} plan={plan} date={date} onClose={() => setTool(null)} />}
      {swap && plan && (
        <MealChangeModal
          plan={plan}
          item={swap}
          onClose={() => setSwap(null)}
        />
      )}
      {detail && <FoodDetail food={detail} onClose={() => setDetail(null)} />}
    </div>
  );
}
function GenerateModal({
  date,
  existingPlan,
  onClose,
  onGenerated,
}: {
  date: string;
  existingPlan?: Plan;
  onClose: () => void;
  onGenerated: (plan: Plan) => void;
}) {
  const { userData: w, update, toast } = useApp();
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [replacementConfirmed, setReplacementConfirmed] = useState(false);
  const [days, setDays] = useState<1 | 7>(1);
  const modeSelectRef = useRef<HTMLSelectElement>(null);
  const replacementBlocked =
    existingPlan &&
    (existingPlan.items.some(
      (item) =>
        item.locked ||
        w.completedMeals.includes(`${existingPlan.id}:${item.id}`),
    )
      ? "Thực đơn có món đang giữ hoặc đã ăn. Hãy bỏ giữ món hoặc chọn ngày khác; chỉ bỏ ghi nhận đã ăn nếu bạn đã đánh dấu nhầm."
      : API_MODE === "api" && w.capabilities?.regenerate === false
        ? "Tạo lại thực đơn tạm thời chưa sẵn sàng. Vui lòng thử lại sau."
        : "");
  const [input, setInput] = useState<GenerateInput>({
    planningMode: existingPlan
      ? existingPlan.planningMode || "MEDICAL"
      : undefined,
    date,
    target: existingPlan?.target || 1800,
    snacks: true,
    maxMinutes: w.capabilities?.cookingTime === false ? 0 : 45,
    excludeRepeat: w.capabilities ? false : true,
  });
  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      assertEligible(w.profile);
      if (!input.planningMode)
        throw new Error("Vui lòng chọn chế độ thực đơn trước khi tạo.");
      if (replacementBlocked) throw new Error(replacementBlocked);
      if (existingPlan && !replacementConfirmed)
        throw new Error("Cần xác nhận thay thế trước khi tạo lại thực đơn.");
      const request: GenerateInput = {
        ...input,
        ...(existingPlan
          ? {
              replacePlanId: existingPlan.id,
              baseVersion: existingPlan.version,
              replacementConfirmed,
            }
          : {}),
      };
      if (days === 7) {
        const end = addDays(input.date, 6);
        const occupied = w.plans.filter(p => p.date >= input.date && p.date <= end);
        if (occupied.length) throw new Error("Khoảng 7 ngày đã có thực đơn. Chọn khoảng trống hoặc tạo lại từng ngày.");
      }
      const created = days === 7
        ? API_MODE === "demo"
          ? Array.from({ length: 7 }, (_, index) => generateDemo({ ...request, date: addDays(request.date, index) }, w))
          : await mealPlanService.generateWeekly(request, w.profile.version)
        : [API_MODE === "demo" ? generateDemo(request, w) : await mealPlanService.generate(request, w.profile.version)];
      if (created.length !== days || new Set(created.map(p => p.date)).size !== days ||
          created.some((p, index) => p.date !== addDays(request.date, index)))
        throw new Error("Chưa nhận được đủ các ngày đã yêu cầu. Tải lại trang để kiểm tra thực đơn đã lưu.");
      const next = created[0];
      if (
        API_MODE === "api" &&
        existingPlan &&
        (next.id !== existingPlan.id || next.version <= existingPlan.version)
      )
        throw new Error(
          "Chưa xác nhận được thực đơn mới. Hãy tải lại trang để kiểm tra.",
        );
      if (
        API_MODE === "api" &&
        created.some(p => !["OPTIMAL", "FEASIBLE"].includes(p.solverStatus))
      )
        throw new Error(
          next.notes.join(" ") ||
            "Chưa có phương án sử dụng được. Thực đơn cũ được giữ nguyên.",
        );
      for (const plan of created) {
        if (API_MODE === "api") assertNewSourceSelection(plan, w.foods);
        sumNutrition(plan.items, w.foods);
      }
      update((s) => ({
        ...s,
        plans: [
          ...created,
          ...s.plans.filter((p) => !created.some(plan => plan.id === p.id || plan.date === p.date)),
        ],
        completedMeals: existingPlan
          ? s.completedMeals.filter(
              (key) => !key.startsWith(`${existingPlan.id}:`),
            )
          : s.completedMeals,
        checkedShopping: existingPlan
          ? s.checkedShopping.filter(
              (key) => !key.startsWith(`${existingPlan.id}:`),
            )
          : s.checkedShopping,
      }));
      onGenerated(next);
      toast(days === 7 ? "Đã tạo và lưu thực đơn 7 ngày." :
        next.solverStatus === "NOT_RUN"
          ? "Đã tạo và lưu thực đơn minh họa."
          : "Đã tạo và lưu thực đơn.",
      );
      onClose();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal
      title={existingPlan ? "Tạo lại thực đơn" : "Tạo thực đơn mới"}
      onClose={onClose}
    >
      <p>
        {existingPlan
          ? "Thực đơn hiện tại chỉ được thay thế khi tạo lại thành công. Bạn có thể chọn lại chế độ; hệ thống không tự chuyển chế độ."
          : "Chọn số ngày và thiết lập thực đơn theo hồ sơ đã lưu của bạn."}
      </p>
      <form onSubmit={submit} className="stack">
        <div className="profile-mini">
          <span className="avatar">{w.profile.name[0]}</span>
          <div>
            <strong>{w.profile.name}</strong>
            <span>
              Hồ sơ của bạn ·{" "}
              {w.profile.allergies.length
                ? `Dị ứng: ${w.profile.allergies.join(", ")}`
                : "Không khai báo dị ứng"}
            </span>
          </div>
          <Link to="/ho-so" onClick={onClose}>
            Sửa
          </Link>
        </div>
        <label>
          Chế độ thực đơn
          <select
            aria-label="Chế độ thực đơn"
            ref={modeSelectRef}
            required
            value={input.planningMode || ""}
            onChange={(e) =>
              setInput({
                ...input,
                planningMode: e.target.value as GenerateInput["planningMode"],
              })
            }
          >
            <option value="" disabled>
              Chọn chế độ thực đơn
            </option>
            <option value="MEDICAL">Bệnh lý (giữ giới hạn GI / GL)</option>
            <option value="GENERAL_REFERENCE">Phổ thông</option>
          </select>
          <small>
            {!input.planningMode
              ? "Chọn Phổ thông cho kế hoạch bữa ăn hằng ngày, hoặc Bệnh lý để áp dụng thêm giới hạn GI/GL."
              : input.planningMode === "GENERAL_REFERENCE"
                ? "Lên kế hoạch theo mục tiêu năng lượng, kiểm tra đạm, chất béo, carbohydrate, natri, dị ứng và món không ăn. Không đánh giá GI/GL; một số chỉ số có thể chưa đủ dữ liệu. Món có thể lặp giữa các ngày. Chế độ này không dùng để điều trị bệnh."
                : "Áp dụng thêm giới hạn GI/GL. Chế độ này cần đủ dữ liệu dinh dưỡng của từng món; nếu chưa có phương án phù hợp, bạn sẽ nhận được thông báo để xem lại lựa chọn."}
          </small>
        </label>
        {!existingPlan && (API_MODE === "demo" || w.capabilities?.weeklyGeneration) && (
          <label>
            Số ngày tạo thực đơn
            <select aria-label="Số ngày tạo thực đơn" value={days} onChange={e => setDays(Number(e.target.value) as 1 | 7)}>
              <option value={1}>1 ngày</option>
              <option value={7}>7 ngày liên tiếp</option>
            </select>
            {days === 7 && <small>Từ {dateLabel(input.date)} đến {dateLabel(addDays(input.date, 6))}. Chỉ tạo khi cả 7 ngày còn trống; các món có thể lặp giữa các ngày.</small>}
          </label>
        )}
        <label>
          {days === 7 ? "Ngày bắt đầu" : "Ngày thực đơn"}
          <input type="date" required value={input.date} readOnly />
        </label>
        <label>
          Mục tiêu năng lượng tham khảo (kcal)
          <input
            type="number"
            min={1000}
            max={4000}
            step={50}
            required
            value={input.target}
            onChange={(e) =>
              setInput({ ...input, target: Number(e.target.value) })
            }
          />
          <small>
            {API_MODE === "demo"
              ? "Giá trị để thử giao diện; bản demo không tối ưu theo mục tiêu này."
              : "Mục tiêu được kiểm tra cùng các điều kiện của thực đơn trước khi tạo."}
          </small>
        </label>
        {w.capabilities?.cookingTime !== false && (
          <label>
            Thời gian nấu tối đa mỗi món
            <select
              value={input.maxMinutes}
              onChange={(e) =>
                setInput({ ...input, maxMinutes: Number(e.target.value) })
              }
            >
              <option value={15}>15 phút</option>
              <option value={30}>30 phút</option>
              <option value={45}>45 phút</option>
              <option value={60}>60 phút</option>
            </select>
          </label>
        )}
        {!w.capabilities && (
          <>
            <Checkbox
              checked={input.snacks}
              disabled={!!w.capabilities}
              onChange={(v) => setInput({ ...input, snacks: v })}
            >
              Thêm bữa phụ
            </Checkbox>
            <Checkbox
              checked={input.excludeRepeat}
              disabled={!!w.capabilities}
              onChange={(v) => setInput({ ...input, excludeRepeat: v })}
            >
              Tránh lặp món trong ngày
            </Checkbox>
          </>
        )}
        {w.capabilities && (
          <Alert>
            Thực đơn gồm bữa sáng, trưa, tối và bữa phụ, tránh lặp món trong
            ngày. Món yêu thích được ưu tiên khi đáp ứng các điều kiện; dị ứng
            và món không ăn được áp dụng theo hồ sơ đã lưu.
          </Alert>
        )}
        <Alert>
          {API_MODE === "demo"
            ? "Chế độ minh họa: chọn món theo bộ lọc, giữ dị ứng và món không ăn. CP-SAT chưa được thực thi."
            : "Thực đơn được kiểm tra bằng bộ tối ưu. Khi không có phương án phù hợp, thực đơn cũ được giữ nguyên."}
        </Alert>
        {existingPlan && (
          <>
            <Alert>
              {API_MODE === "api"
                ? "Xác nhận sẽ thay thế toàn bộ thực đơn của ngày này, kể cả khi chọn chế độ khác. Bản cũ và các quy tắc đã áp dụng được lưu để xem lại."
                : "Xác nhận sẽ thay thế thực đơn minh họa của ngày này."}
            </Alert>
            <Checkbox
              checked={replacementConfirmed}
              disabled={!!replacementBlocked || busy}
              onChange={setReplacementConfirmed}
            >
              Tôi xác nhận thay thế thực đơn hiện tại
            </Checkbox>
            {replacementBlocked && <Alert error>{replacementBlocked}</Alert>}
          </>
        )}
        {error && <Alert error>{error}</Alert>}
        {!input.planningMode && (
          <Alert>
            <span>Chọn chế độ thực đơn ở phía trên để bật nút tạo.</span>{" "}
            <Button
              type="button"
              variant="ghost"
              onClick={() => {
                modeSelectRef.current?.scrollIntoView?.({
                  behavior: "smooth",
                  block: "center",
                });
                modeSelectRef.current?.focus({ preventScroll: true });
              }}
            >
              Đi tới chọn chế độ
            </Button>
          </Alert>
        )}
        <div className="modal-actions">
          <Button type="button" variant="secondary" onClick={onClose}>
            Hủy
          </Button>
          <Button
            busy={busy}
            type="submit"
            disabled={
              !input.planningMode ||
              !!replacementBlocked ||
              (!!existingPlan && !replacementConfirmed)
            }
          >
            {existingPlan
              ? "Xác nhận tạo lại"
              : days === 7 ? "Tạo thực đơn 7 ngày" : API_MODE === "demo"
                ? "Tạo & lưu mẫu"
                : "Tạo bằng bộ tối ưu"}
            <ArrowRight size={16} />
          </Button>
        </div>
      </form>
    </Modal>
  );
}
