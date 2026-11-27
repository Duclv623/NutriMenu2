import { Link } from "react-router-dom";
import { useState } from "react";
import { ArrowRight, Download, RefreshCw, ShieldCheck } from "lucide-react";
import { Alert, Badge, Button, PageTitle } from "../../../components/common/ui";
import { useApp } from "../../../store/UserDataProvider";
import { rag } from "../../chatbot/services/chatService";
import { downloadFile, localDate } from "../../../utils";
export function SettingsPage() {
  const { userData: w, user } = useApp();
  const [status, setStatus] = useState<Record<string, unknown> | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function check() {
    setBusy(true);
    setError("");
    setStatus(null);
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
        title="Cài đặt"
        description="Quản lý thông tin cá nhân và dữ liệu của bạn."
      />
      <div className="settings-layout">
        <div className="stack">
          <section className="panel form-panel">
            <h2>Tài khoản của bạn</h2>
            <div className="settings-row">
              <span>Họ tên</span>
              <strong>{w.profile.name || user?.name}</strong>
            </div>
            <div className="settings-row">
              <span>Email</span>
              <strong>{user?.email}</strong>
            </div>
            <Link className="text-link" to="/ho-so">
              Chỉnh sửa hồ sơ dinh dưỡng <ArrowRight size={15} />
            </Link>
          </section>
          <section className="panel form-panel">
            <h2>Dữ liệu cá nhân</h2>
            <p>
              Tải bản sao hồ sơ, thực đơn và nhật ký hiện đang có trong tài
              khoản. Nội dung hội thoại trong bản sao chỉ gồm các cuộc trò
              chuyện đã được tải trên thiết bị này.
            </p>
            <Button
              variant="secondary"
              onClick={() =>
                downloadFile(
                  `nutriguide-${localDate()}.json`,
                  JSON.stringify(
                    { exportedAt: new Date().toISOString(), ...w },
                    null,
                    2,
                  ),
                  "application/json",
                )
              }
            >
              <Download size={16} />
              Tải bản sao dữ liệu
            </Button>
            <p className="fineprint">
              Bản sao có thể chứa thông tin sức khỏe. Hãy lưu ở nơi riêng tư.
            </p>
          </section>
          <section className="panel form-panel">
            <div className="section-title">
              <div>
                <h2>Trợ lý dinh dưỡng</h2>
                <p>Kiểm tra khả năng truy cập tài liệu và trả lời câu hỏi.</p>
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
            {!status && !error && (
              <p className="muted">Chưa kiểm tra trạng thái kết nối.</p>
            )}
            {status && (
              <div className="stack">
                <div className="settings-row">
                  <span>Tài liệu tham khảo</span>
                  <Badge tone={status.index_ready ? "green" : "amber"}>
                    {status.index_ready ? "Sẵn sàng" : "Tạm thời chưa sẵn sàng"}
                  </Badge>
                </div>
                <div className="settings-row">
                  <span>Trả lời câu hỏi</span>
                  <Badge tone={status.provider_configured ? "green" : "amber"}>
                    {status.provider_configured
                      ? "Đã thiết lập"
                      : "Tạm thời chưa sẵn sàng"}
                  </Badge>
                </div>
                <p className="fineprint">
                  Trạng thái thiết lập không bảo đảm mọi câu hỏi đều được trả
                  lời. Nếu gặp lỗi, hãy thử lại sau.
                </p>
                <Link className="text-link" to="/tro-ly">
                  Mở trợ lý <ArrowRight size={15} />
                </Link>
              </div>
            )}
          </section>
        </div>
        <aside className="panel settings-about">
          <span className="brand-icon">
            <ShieldCheck size={24} />
          </span>
          <h2>Hiểu rõ dữ liệu của bạn.</h2>
          <p>
            Bạn chủ động cập nhật hồ sơ, ghi nhận bữa ăn và quản lý các cuộc trò
            chuyện đã lưu.
          </p>
          <Link className="text-link" to="/lich-su">
            Lịch sử thực đơn <ArrowRight size={15} />
          </Link>
          <Link className="text-link" to="/theo-doi">
            Nhật ký sức khỏe <ArrowRight size={15} />
          </Link>
          <Link className="text-link" to="/tro-ly">
            Quản lý hội thoại <ArrowRight size={15} />
          </Link>
          <small>NutriGuide</small>
        </aside>
      </div>
    </div>
  );
}
