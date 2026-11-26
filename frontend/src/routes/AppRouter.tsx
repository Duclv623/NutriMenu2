import { API_MODE } from "../lib/config";
import {
  Component,
  useEffect,
  useState,
  type ErrorInfo,
  type ReactNode,
} from "react";
import {
  NavLink,
  Navigate,
  Outlet,
  Route,
  Routes,
  useLocation,
  Link,
} from "react-router-dom";
import {
  Activity,
  CalendarDays,
  ChartNoAxesCombined,
  Check,
  ChevronDown,
  Heart,
  LayoutDashboard,
  Leaf,
  LogOut,
  Menu,
  MessageSquare,
  Search,
  Settings,
  ShoppingBasket,
  UtensilsCrossed,
  X,
} from "lucide-react";
import { useApp } from "../store/UserDataProvider";

import { Alert, Button, IconButton, Loading } from "../components/common/ui";
import { DashboardPage } from "../features/dashboard/pages/DashboardPage";
import { MealPlannerPage } from "../features/meals/pages/MealPlannerPage";
import { FoodLibraryPage } from "../features/catalog/pages/FoodLibraryPage";
import { ProfilePage } from "../features/profile/pages/ProfilePage";
import { ChatPage } from "../features/chatbot/pages/ChatPage";
import { HistoryPage } from "../features/meals/pages/HistoryPage";
import { ProgressPage } from "../features/activity/pages/ProgressPage";
import { ShoppingPage } from "../features/shopping/pages/ShoppingPage";
import { ServiceAdministration } from "../features/admin/pages/ServiceAdministration";
import { SettingsPage } from "../features/settings/pages/SettingsPage";
import { AuthPage } from "../features/auth/pages/AuthPage";
const navigation = [
  { to: "/", label: "Tổng quan", icon: LayoutDashboard },
  { to: "/thuc-don", label: "Thực đơn của tôi", icon: CalendarDays },
  { to: "/mon-an", label: "Khám phá món ăn", icon: UtensilsCrossed },
  { to: "/tro-ly", label: "Trợ lý dinh dưỡng", icon: MessageSquare },
  { to: "/theo-doi", label: "Theo dõi sức khỏe", icon: ChartNoAxesCombined },
];
const personal = [
  { to: "/yeu-thich", label: "Món yêu thích", icon: Heart },
  { to: "/mua-sam", label: "Danh sách mua sắm", icon: ShoppingBasket },
  { to: "/lich-su", label: "Lịch sử thực đơn", icon: Activity },
];
function Shell() {
  const {
    user,
    userData,
    loading,
    loadError,
    storageError,
    reload,
    logout,
    notice,
    toast,
  } = useApp();
  const [open, setOpen] = useState(false);
  const location = useLocation();
  const isChat = location.pathname.startsWith("/tro-ly");
  useEffect(() => {
    setOpen(false);
    window.scrollTo(0, 0);
  }, [location.pathname]);
  useEffect(() => {
    if (open) document.getElementById("mobile-nav-close")?.focus();
  }, [open]);
  function closeMenu() {
    setOpen(false);
    document.getElementById("mobile-nav-toggle")?.focus();
  }
  if (!user) return <Navigate to="/dang-nhap" replace />;
  if (
    API_MODE === "api" &&
    !loading &&
    !loadError &&
    !userData.profile.dateOfBirth &&
    location.pathname === "/"
  )
    return <Navigate to="/ho-so" replace />;
  const title =
    [
      ...navigation,
      ...personal,
      { to: "/ho-so", label: "Hồ sơ dinh dưỡng" },
      { to: "/cai-dat", label: "Cài đặt" },
    ].find((n) => n.to === location.pathname)?.label || "NutriGuide";
  return (
    <div className="app-shell">
      {open && <div className="sidebar-overlay" onClick={closeMenu} />}
      <aside
        id="app-sidebar"
        className={`sidebar ${open ? "open" : ""}`}
        onKeyDown={(event) => {
          if (open && event.key === "Escape") closeMenu();
        }}
      >
        <Link to="/" className="brand">
          <span className="brand-icon">
            <Leaf size={24} />
          </span>
          <span>
            NutriGuide<small>ĂN LÀNH · SỐNG KHỎE</small>
          </span>
        </Link>
        <IconButton
          label="Đóng menu"
          id="mobile-nav-close"
          className="mobile-close"
          onClick={closeMenu}
        >
          <X size={20} />
        </IconButton>
        <div className="nav-label">KHÔNG GIAN CỦA BẠN</div>
        <nav aria-label="Điều hướng chính">
          {navigation.map((n) => (
            <NavLink key={n.to} to={n.to} end={n.to === "/"}>
              <n.icon size={19} />
              <span>{n.label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="nav-label personal-label">THƯ VIỆN CÁ NHÂN</div>
        <nav aria-label="Thư viện cá nhân">
          {personal.map((n) => (
            <NavLink key={n.to} to={n.to}>
              <n.icon size={19} />
              <span>{n.label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <nav>
            {user.role === "ADMIN" && <NavLink to="/quan-tri">Quản trị</NavLink>}
            <NavLink to="/cai-dat">
              <Settings size={19} />
              Cài đặt & kết nối
            </NavLink>
            <button
              onClick={() => {
                void logout().catch((e) => toast(e.message));
              }}
            >
              <LogOut size={18} />
              Đăng xuất
            </button>
          </nav>
        </div>
      </aside>
      <div className="main-shell">
        <header className="topbar">
          <div className="breadcrumb">
            <IconButton
              id="mobile-nav-toggle"
              className="mobile-menu"
              label="Mở menu"
              aria-expanded={open}
              aria-controls="app-sidebar"
              onClick={() => setOpen(true)}
            >
              <Menu size={21} />
            </IconButton>
            <span>Không gian cá nhân</span>
            <span className="slash">/</span>
            <strong>{title}</strong>
          </div>
          <div className="topbar-right">
            <Link
              to="/mon-an"
              className="search-shortcut"
              aria-label="Tìm món ăn"
            >
              <Search size={17} />
              <span>Tìm món ăn…</span>
            </Link>
            <span className={`mode-dot ${API_MODE}`}>
              {API_MODE === "demo" ? "Bản demo" : "Kết nối API"}
            </span>
            <Link className="user-menu" to="/ho-so" aria-label="Hồ sơ cá nhân">
              <span className="avatar">
                {(userData.profile.name || user.name)
                  .split(" ")
                  .slice(-2)
                  .map((s) => s[0])
                  .join("")}
              </span>
              <span>
                {userData.profile.name || user.name}
                <small>Hồ sơ cá nhân</small>
              </span>
              <ChevronDown size={14} />
            </Link>
          </div>
        </header>
        <main id="main-content" className={`content ${isChat ? "chat-page-container" : ""}`}>
          {storageError && <Alert error>{storageError}</Alert>}
          {loading ? (
            <Loading />
          ) : loadError ? (
            <div className="stack">
              <Alert error>{loadError}</Alert>
              <Button onClick={() => void reload()}>Thử kết nối lại</Button>
            </div>
          ) : (
            <Outlet />
          )}
        </main>
        {!isChat && (
          <footer className="footer">
            <span>NutriGuide · Đồng hành cùng bữa ăn Việt</span>
            <span>Thông tin tham khảo, không thay thế tư vấn chuyên môn.</span>
          </footer>
        )}
      </div>
      {notice && (
        <div className="toast" role="status">
          <Check size={18} />
          {notice}
        </div>
      )}
    </div>
  );
}
class ErrorBoundary extends Component<
  { children: ReactNode },
  { error: boolean }
> {
  state = { error: false };
  static getDerivedStateFromError() {
    return { error: true };
  }
  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("UI error", error.message, info.componentStack);
  }
  render() {
    return this.state.error ? (
      <div className="fatal">
        <h1>Không thể hiển thị trang này</h1>
        <p>
          Dữ liệu có thể chưa đúng định dạng. Hãy tải lại hoặc kiểm tra kết nối.
        </p>
        <Button onClick={() => window.location.reload()}>Tải lại trang</Button>
      </div>
    ) : (
      this.props.children
    );
  }
}
export function AppRouter() {
  return (
    <ErrorBoundary>
      <a className="skip-link" href="#main-content">
        Đến nội dung chính
      </a>
      <Routes>
        <Route path="/dang-nhap" element={<AuthPage />} />
        <Route path="/dang-ky" element={<AuthPage register />} />
        <Route element={<Shell />}>
          <Route index element={<DashboardPage />} />
          <Route path="thuc-don" element={<MealPlannerPage />} />
          <Route path="mon-an" element={<FoodLibraryPage />} />
          <Route path="yeu-thich" element={<FoodLibraryPage favoritesOnly />} />
          <Route path="ho-so" element={<ProfilePage />} />
          <Route path="tro-ly" element={<ChatPage />} />
          <Route path="mua-sam" element={<ShoppingPage />} />
          <Route path="lich-su" element={<HistoryPage />} />
          <Route path="theo-doi" element={<ProgressPage />} />
          <Route path="quan-tri" element={<ServiceAdministration />} />
          <Route path="cai-dat" element={<SettingsPage />} />
          <Route
            path="*"
            element={
              <div className="empty">
                <h1>Không tìm thấy trang</h1>
                <Link className="button primary" to="/">
                  Về tổng quan
                </Link>
              </div>
            }
          />
        </Route>
      </Routes>
    </ErrorBoundary>
  );
}
