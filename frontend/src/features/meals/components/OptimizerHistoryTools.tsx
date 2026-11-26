import { useEffect, useState } from "react";
import { Alert, Button, Loading } from "../../../components/common/ui";
import { useApp } from "../../../store/UserDataProvider";
import { number } from "../../../utils";
import { mealInsightsService, type Analytics } from "../services/mealInsightsService";

export function OptimizerHistoryTools() {
  const { userData } = useApp();
  const [days, setDays] = useState("7");
  const [recentDays, setRecentDays] = useState("3");
  const [analytics, setAnalytics] = useState<Analytics | null>(null);
  const [recent, setRecent] = useState<number[] | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [recentLoading, setRecentLoading] = useState(false);
  const [recentError, setRecentError] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    setAnalytics(null);
    setError("");
    setLoading(true);
    void mealInsightsService.analytics(days, controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setAnalytics(data);
      })
      .catch((failure: Error) => {
        if (!controller.signal.aborted) setError(failure.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [days]);
  async function loadRecent() {
    setRecentLoading(true);
    setRecentError("");
    setRecent(null);
    try {
      setRecent(
        await mealInsightsService.recent(recentDays),
      );
    } catch (failure) {
      setRecentError((failure as Error).message);
    } finally {
      setRecentLoading(false);
    }
  }
  return (
    <div className="workflow-comparison">
      <section className="panel workflow-panel stack">
        <h2>Thống kê dinh dưỡng</h2>
        <label>
          Khoảng thống kê
          <select
            value={days}
            onChange={(event) => setDays(event.target.value)}
          >
            {["7", "14", "30", "90"].map((value) => (
              <option key={value} value={value}>
                {value} ngày
              </option>
            ))}
          </select>
        </label>
        {loading && <Loading />}
        {error && <Alert error>{error}</Alert>}
        {analytics &&
          (analytics.completedItemsCount ? (
            <dl className="service-result-fields">
              {[
                ["Món đã ăn", String(analytics.completedItemsCount)],
                ["Tỷ lệ ghi nhận", number(analytics.adherenceRatePct, 1) + "%"],
                [
                  "Năng lượng/ngày",
                  number(analytics.avgDailyCaloriesKcal, 1) + " kcal",
                ],
                [
                  "Carb thuần/ngày",
                  number(analytics.avgDailyNetCarbG, 1) + " g",
                ],
                ["Chất xơ/ngày", number(analytics.avgDailyFiberG, 1) + " g"],
                ["GL/ngày", number(analytics.avgDailyGl, 1)],
              ].map(([label, value]) => (
                <div key={label}>
                  <dt>{label}</dt>
                  <dd>{value}</dd>
                </div>
              ))}
            </dl>
          ) : (
            <p>Chưa có món đã ăn được ghi nhận trong giai đoạn này.</p>
          ))}
      </section>
      <section className="panel workflow-panel stack">
        <h2>Món trong thực đơn gần đây</h2>
        <form
          className="stack"
          onSubmit={(event) => {
            event.preventDefault();
            void loadRecent();
          }}
        >
          <label>
            Số ngày
            <input
              type="number"
              min={1}
              max={365}
              required
              value={recentDays}
              onChange={(event) => {
                setRecentDays(event.target.value);
                setRecent(null);
              }}
            />
          </label>
          <div>
            <Button type="submit" variant="secondary" busy={recentLoading}>
              Xem món gần đây
            </Button>
          </div>
        </form>
        {recentError && <Alert error>{recentError}</Alert>}
        {recent &&
          (recent.length ? (
            <ul>
              {recent.map((id) => (
                <li key={id}>
                  {userData.foods.find((food) => food.id === String(id))
                    ?.name ?? "Món #" + id}
                </li>
              ))}
            </ul>
          ) : (
            <p>Chưa có món trong các thực đơn của khoảng ngày này.</p>
          ))}
      </section>
    </div>
  );
}
