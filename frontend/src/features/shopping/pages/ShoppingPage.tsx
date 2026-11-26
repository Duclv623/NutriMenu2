import { API_MODE } from "../../../lib/config";
import { activityService } from "../../activity/services/activityService";
import { useMemo, useState } from "react";

import { Link, useSearchParams } from "react-router-dom";

import { ArrowRight, Check, Download, ShoppingBasket } from "lucide-react";

import { Alert, Badge, Button, Checkbox, Empty, PageTitle } from "../../../components/common/ui";

import { useApp } from "../../../store/UserDataProvider";



import { dateLabel, downloadFile, localDate, number, shoppingList } from "../../../utils";

import { OptimizerShopping } from "./OptimizerShopping";

export function ShoppingPage() {
  return API_MODE === "api" ? <OptimizerShopping /> : <DemoShoppingPage />;
}

function DemoShoppingPage() {
  const { userData: w, update, toast } = useApp();
  const [params, setParams] = useSearchParams();
  const [busy, setBusy] = useState(false);
  const plan =
    w.plans.find((p) => p.id === params.get("plan")) ||
    w.plans.find((p) => p.date === localDate()) ||
    w.plans[0];
  const shopping = useMemo(() => {
    try {
      return {
        ingredients: plan ? shoppingList(plan, w.foods) : [],
        error: "",
      };
    } catch (e) {
      return { ingredients: [], error: (e as Error).message };
    }
  }, [plan, w.foods]);
  const { ingredients, error: shoppingError } = shopping;
  const done = ingredients.filter((i) =>
    w.checkedShopping.includes(`${plan?.id}:${i.id}`),
  ).length;
  async function check(id: string, value: boolean) {
    setBusy(true);
    const key = `${plan!.id}:${id}`;
    const next = value
      ? [...w.checkedShopping, key]
      : w.checkedShopping.filter((x) => x !== key);
    try {
      if (API_MODE === "api") await activityService.shopping(next);
      update((s) => ({ ...s, checkedShopping: next }));
    } catch (e) {
      toast((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  const exportList = () => {
    if (!plan) return;
    downloadFile(
      `nguyen-lieu-${plan.date}.txt`,
      [
        `NutriGuide · Danh sách nguyên liệu ${dateLabel(plan.date)}`,
        ...ingredients.map(
          (i) =>
            `${w.checkedShopping.includes(`${plan.id}:${i.id}`) ? "[x]" : "[ ]"} ${i.name}: ${number(i.grams)} g`,
        ),
        API_MODE === "api"
          ? "\nKhối lượng nguyên liệu sống theo công thức hiện tại trong danh mục; kiểm tra lại trước khi mua."
          : "\nKhối lượng theo công thức minh họa; kiểm tra trạng thái sống/chín trong dữ liệu nguồn.",
      ].join("\n"),
    );
  };
  return (
    <div className="stack-lg">
      <PageTitle
        eyebrow="CHUẨN BỊ CHO BỮA ĂN"
        title="Đi chợ, nhẹ nhàng hơn."
        description="Nguyên liệu được gom từ những món trong thực đơn bạn chọn."
        actions={
          <Button
            variant="secondary"
            disabled={!plan || !!shoppingError}
            onClick={exportList}
          >
            <Download size={16} />
            Tải danh sách
          </Button>
        }
      />
      {shoppingError && <Alert error>{shoppingError}</Alert>}
      {plan ? (
        <div className="shopping-layout">
          <section className="panel shopping-list">
            <div className="shopping-header">
              <label>
                Thực đơn
                <select
                  value={plan.id}
                  onChange={(e) => setParams({ plan: e.target.value })}
                >
                  {w.plans.map((p) => (
                    <option key={p.id} value={p.id}>
                      {dateLabel(p.date, {
                        weekday: "short",
                        day: "numeric",
                        month: "numeric",
                        year: "numeric",
                      })}{" "}
                      · {p.items.length} món · {p.id.slice(-5)}
                    </option>
                  ))}
                </select>
              </label>
              <Badge tone="neutral">
                {done}/{ingredients.length} đã có
              </Badge>
            </div>
            <div className="shopping-progress">
              <span
                style={{
                  width: `${ingredients.length ? (done / ingredients.length) * 100 : 0}%`,
                }}
              />
            </div>
            {ingredients.map((i) => {
              const checked = w.checkedShopping.includes(`${plan.id}:${i.id}`);
              return (
                <div
                  className={`shopping-item ${checked ? "done" : ""}`}
                  key={i.id}
                >
                  <Checkbox
                    checked={checked}
                    onChange={(value) => {
                      if (!busy) void check(i.id, value);
                    }}
                  >
                    {i.name}
                  </Checkbox>
                  <strong>
                    {number(i.grams)} <small>g</small>
                  </strong>
                </div>
              );
            })}
            {done === ingredients.length && ingredients.length > 0 && (
              <div className="shopping-ready">
                <Check size={19} />
                Bạn đã có đủ nguyên liệu cho thực đơn này.
              </div>
            )}
          </section>
          <aside className="stack">
            <div className="daily-note shopping-note">
              <ShoppingBasket size={30} strokeWidth={1.5} />
              <h2>
                Một danh sách.
                <br />
                Đủ cho cả ngày.
              </h2>
              <p>
                Nguyên liệu trùng nhau được cộng dồn theo đúng khẩu phần từng
                món.
              </p>
              <Link to={`/thuc-don?date=${plan.date}&plan=${plan.id}`}>
                Xem lại thực đơn
                <ArrowRight size={16} />
              </Link>
            </div>
            <Alert>
              {plan.dataMode === "demo"
                ? "Khối lượng là dữ liệu mẫu. Cơm trong danh sách đã ở trạng thái chín; không quy đổi thành gạo sống."
                : "Kiểm tra trạng thái nguyên liệu sống/chín theo công thức trước khi mua."}
            </Alert>
          </aside>
        </div>
      ) : (
        <Empty
          title="Chưa có danh sách nguyên liệu"
          action={
            <Link className="button primary" to="/thuc-don">
              Tạo thực đơn
            </Link>
          }
        >
          Tạo một thực đơn để bắt đầu chuẩn bị nguyên liệu.
        </Empty>
      )}
    </div>
  );
}
