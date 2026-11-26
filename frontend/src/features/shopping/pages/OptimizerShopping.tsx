import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Download } from "lucide-react";
import { Alert, Button, Checkbox, Empty, Loading, PageTitle } from "../../../components/common/ui";
import { activityService } from "../../activity/services/activityService";
import { mealInsightsService, type Shopping } from "../../meals/services/mealInsightsService";
import { useApp } from "../../../store/UserDataProvider";
import { dateLabel, downloadFile, localDate, number } from "../../../utils";

export function OptimizerShopping() {
  const { userData, update, toast } = useApp();
  const [params] = useSearchParams();
  const date =
    userData.plans.find((plan) => plan.id === params.get("plan"))?.date ??
    localDate();
  const [from, setFrom] = useState(date);
  const [to, setTo] = useState(date);
  const [range, setRange] = useState({ fromDate: date, toDate: date });
  const [result, setResult] = useState<Shopping | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    setFrom(date);
    setTo(date);
    setRange({ fromDate: date, toDate: date });
  }, [date]);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError("");
    setResult(null);
    void mealInsightsService.shopping(range, controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setResult(data);
      })
      .catch((failure: Error) => {
        if (!controller.signal.aborted) setError(failure.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [range]);
  const single = range.fromDate === range.toDate;
  const plan = single
    ? userData.plans.find((entry) => entry.date === range.fromDate)
    : undefined;
  const prefix = plan?.id ?? range.fromDate + "/" + range.toDate;
  const keyFor = (id: number) => prefix + ":" + id;
  const checked = (id: number) =>
    userData.checkedShopping.includes(keyFor(id));
  const count =
    result?.items.filter((item) => checked(item.ingredientId)).length ?? 0;
  async function toggle(id: number, selected: boolean) {
    setSaving(true);
    const key = keyFor(id);
    const next = selected
      ? [...new Set([...userData.checkedShopping, key])]
      : userData.checkedShopping.filter((value) => value !== key);
    try {
      await activityService.shopping(next);
      update((state) => ({ ...state, checkedShopping: next }));
    } catch (failure) {
      toast((failure as Error).message);
    } finally {
      setSaving(false);
    }
  }
  function download() {
    if (!result) return;
    const lines = [
      [range.fromDate, ...(!single ? [range.toDate] : [])]
        .map((entry) => dateLabel(entry))
        .join(" – "),
      ...result.items.map(
        (item) =>
          (checked(item.ingredientId) ? "[x] " : "[ ] ") +
          item.ingredientNameVi +
          " · " +
          number(item.totalRawWeightG, 1) +
          " g",
      ),
    ];
    downloadFile("nguyen-lieu-" + range.fromDate + ".txt", lines.join("\n"));
  }
  return (
    <div className="stack-lg">
      <PageTitle
        title="Danh sách mua sắm"
        actions={
          <Button
            variant="secondary"
            disabled={!result?.items.length}
            onClick={download}
          >
            <Download size={17} />
            Tải danh sách
          </Button>
        }
      />
      <form
        className="panel workflow-panel workflow-date-form"
        onSubmit={(event) => {
          event.preventDefault();
          setRange({ fromDate: from, toDate: to });
        }}
      >
        <label>
          Từ ngày
          <input
            type="date"
            required
            value={from}
            max={to}
            onChange={(event) => setFrom(event.target.value)}
          />
        </label>
        <label>
          Đến ngày
          <input
            type="date"
            required
            value={to}
            min={from}
            onChange={(event) => setTo(event.target.value)}
          />
        </label>
        <Button type="submit" busy={loading}>
          Lấy danh sách
        </Button>
      </form>
      {loading && <Loading />}
      {error && <Alert error>{error}</Alert>}
      {result &&
        (result.items.length ? (
          <section className="panel workflow-panel stack" aria-live="polite">
            <div className="workflow-result-row">
              <h2>Nguyên liệu sống</h2>
              <span>
                {count}/{result.items.length} đã mua
              </span>
            </div>
            <div className="ingredient-list">
              {result.items.map((item) => (
                <div key={item.ingredientId}>
                  <Checkbox
                    checked={checked(item.ingredientId)}
                    disabled={saving}
                    onChange={(value) => void toggle(item.ingredientId, value)}
                  >
                    {item.ingredientNameVi}
                  </Checkbox>
                  <strong>{number(item.totalRawWeightG, 1)} g</strong>
                </div>
              ))}
            </div>
          </section>
        ) : (
          <Empty title="Chưa có nguyên liệu trong khoảng ngày này">
            Chọn ngày có thực đơn đã lưu.
          </Empty>
        ))}
    </div>
  );
}

