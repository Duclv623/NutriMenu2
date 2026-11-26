import { API_MODE, RAG_ENABLED } from "../../../lib/config";
import { rag } from "../../chatbot/services/chatService";
import { useState } from "react";


import { Check, Download, RefreshCw, ShieldCheck, Trash2 } from "lucide-react";

import { Alert, Badge, Button, Confirm, PageTitle } from "../../../components/common/ui";

import { useApp } from "../../../store/UserDataProvider";



import { downloadFile, localDate } from "../../../utils";


import { createDemoData } from "../../../data/seed";

export function SettingsPage() {
  const { userData: w, update, toast } = useApp();
  const [status, setStatus] = useState<Record<string, unknown> | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [reset, setReset] = useState(false);
  async function check() {
    setBusy(true);
    setError("");
    try {
      setStatus(await rag.status());
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="stack-lg">
      <PageTitle
        eyebrow="KHÔNG GIAN CỦA BẠN"
        title="Cài đặt & kết nối"
        description="Kiểm tra nguồn dữ liệu và chuẩn bị kết nối các dịch vụ của nhóm."
      />
      <div className="settings-layout">
        <div className="stack">
          <section className="panel form-panel">
            <div className="section-title">
              <div>
                <h2>Nguồn dữ liệu ứng dụng</h2>
                <p>Chế độ đang chạy được cấu hình khi khởi động frontend.</p>
              </div>
              <Badge tone={API_MODE === "demo" ? "amber" : "green"}>
                {API_MODE === "demo" ? "Dữ liệu demo" : "Backend API"}
              </Badge>
            </div>
            <div className="settings-row">
              <span>Hồ sơ, món ăn & thực đơn</span>
              <strong>
                {API_MODE === "demo" ? "Lưu trên trình duyệt" : "API Gateway"}
              </strong>
            </div>
            <div className="settings-row">
              <span>Chatbot</span>
              <strong>
                {RAG_ENABLED ? "Kết nối RAG thật" : "Xem trước giao diện"}
              </strong>
            </div>
            <div className="settings-row">
              <span>Bộ tối ưu CP-SAT</span>
              <strong>
                {API_MODE === "demo"
                  ? "Chưa thực thi"
                  : "Do Recommendation Service xử lý"}
              </strong>
            </div>
            <Alert>
              {API_MODE === "demo"
                ? "Bản demo cho phép thử các thao tác giao diện. Các con số là fixture, không phải khẩu phần đã được tối ưu cho cá nhân."
                : "Khi dịch vụ thật gặp lỗi, ứng dụng hiển thị lỗi và giữ dữ liệu hiện tại. Không tự thay bằng dữ liệu demo."}
            </Alert>
          </section>
          <section className="panel form-panel">
            <div className="section-title">
              <div>
                <h2>Kết nối chatbot hiện có</h2>
                <p>Kiểm tra dịch vụ FastAPI của nhóm tại cổng 8000.</p>
              </div>
              <Button
                variant="secondary"
                busy={busy}
                onClick={() => void check()}
              >
                <RefreshCw size={15} />
                Kiểm tra
              </Button>
            </div>
            {error && <Alert error>{error}</Alert>}
            {status && (
              <div className="stack">
                <div className="settings-row">
                  <span>Chỉ mục tài liệu</span>
                  <Badge tone={status.index_ready ? "green" : "amber"}>
                    {status.index_ready ? "Sẵn sàng" : "Chưa sẵn sàng"}
                  </Badge>
                </div>
                <div className="settings-row">
                  <span>Nhà cung cấp AI</span>
                  <strong>
                    {status.provider_configured
                      ? "Đã cấu hình"
                      : "Chưa cấu hình"}
                  </strong>
                </div>
                <div className="settings-row">
                  <span>Số đoạn tài liệu</span>
                  <strong>{String(status.chunk_count ?? "—")}</strong>
                </div>
              </div>
            )}
            <div className="connection-guide">
              <h3>Khi tích hợp backend</h3>
              <p>
                Hướng dẫn cấu hình địa chỉ API, hợp đồng dữ liệu và cách chạy
                chatbot nằm trong tài liệu đi kèm mã nguồn.
              </p>
              <code>frontend/README.md</code>
              <p>
                Không đưa khóa Gemini hoặc các khóa bí mật vào frontend. Các
                biến bắt đầu bằng VITE_ đều hiển thị trong trình duyệt.
              </p>
            </div>
          </section>
          {API_MODE === "demo" && (
            <section className="panel form-panel">
              <h2>Dữ liệu demo trên trình duyệt</h2>
              <p>
                Tải bản sao hoặc đưa không gian hiện tại về dữ liệu ban đầu.
              </p>
              <div className="actions">
                <Button
                  variant="secondary"
                  onClick={() =>
                    downloadFile(
                      `nutriguide-demo-${localDate()}.json`,
                      JSON.stringify(w, null, 2),
                      "application/json",
                    )
                  }
                >
                  <Download size={16} />
                  Sao lưu demo
                </Button>
                <Button variant="danger" onClick={() => setReset(true)}>
                  <Trash2 size={16} />
                  Đặt lại demo
                </Button>
              </div>
            </section>
          )}
        </div>
        <aside className="panel settings-about">
          <span className="brand-icon">
            <ShieldCheck size={24} />
          </span>
          <h2>Rõ ràng từ dữ liệu đến trải nghiệm.</h2>
          <p>
            Frontend chuẩn bị các luồng tương tác. Backend chịu trách nhiệm xác
            thực, tính dinh dưỡng và kiểm tra kết quả tối ưu.
          </p>
          <div>
            <Check size={15} />
            Tách riêng lớp kết nối API
          </div>
          <div>
            <Check size={15} />
            Quản lý phiên bản hồ sơ
          </div>
          <div>
            <Check size={15} />
            Hiển thị trạng thái bộ tối ưu
          </div>
          <div>
            <Check size={15} />
            Trích dẫn từ chatbot hiện có
          </div>
          <small>NutriGuide · Phiên bản frontend 1.0</small>
        </aside>
      </div>
      {reset && (
        <Confirm
          title="Đặt lại dữ liệu demo?"
          description="Hồ sơ, thực đơn, nhật ký và hội thoại trong không gian demo hiện tại sẽ được đưa về ban đầu. Tải bản sao trước nếu muốn giữ lại."
          onClose={() => setReset(false)}
          onConfirm={async () => {
            update(() => createDemoData(w.profile.name));
            toast("Đã khôi phục dữ liệu demo ban đầu.");
          }}
        />
      )}
    </div>
  );
}
