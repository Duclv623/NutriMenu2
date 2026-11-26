import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import type { User, UserData } from "../types";
import { createDemoData } from "../data/seed";
import { useLocation } from "react-router-dom";
import { API_MODE } from "../lib/config";
import { logoutAccount } from "../features/auth/services/authService";
import { loadPageData } from "./loadPageData";
export const emptyUserData = (): UserData => ({
  profile: {
    name: "",
    dateOfBirth: "",
    age: 0,
    gender: "",
    height: 0,
    weight: 0,
    targetWeight: 0,
    activity: "",
    goal: "",
    allergies: [],
    excludedFoods: [],
    preferences: "",
    pregnant: false,
    specialDiet: false,
    confirmed: false,
    version: 0,
  },
  foods: [],
  plans: [],
  favorites: [],
  checkedShopping: [],
  checkIns: [],
  conversations: [],
  completedMeals: [],
});
type Context = {
  user: User | null;
  userData: UserData;
  loading: boolean;
  loadError: string;
  storageError: string;
  login: (user: User) => void;
  logout: () => Promise<void>;
  reload: (background?: boolean) => Promise<void>;
  update: (fn: (state: UserData) => UserData) => void;
  toast: (message: string) => void;
  notice: string;
};
const AppContext = createContext<Context | null>(null);
function getUser(): User | null {
  try {
    return JSON.parse(sessionStorage.getItem("nutriguide.user") || "null");
  } catch {
    return null;
  }
}
export function UserDataProvider({ children }: { children: ReactNode }) {
  const { pathname } = useLocation();
  const page = API_MODE === "api" ? pathname.replace(/\/+$/, "") || "/" : "/";
  const [loadedPage, setLoadedPage] = useState("");
  const [user, setUser] = useState<User | null>(getUser);
  const [userData, setUserData] = useState<UserData>(() =>
    API_MODE === "api" ? emptyUserData() : createDemoData(),
  );
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [storageError, setStorageError] = useState("");
  const [notice, setNotice] = useState("");
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const ownerRef = useRef<string | null>(null);
  const loadTicket = useRef(0);
  const toast = useCallback((message: string) => {
    setNotice(message);
    clearTimeout(timer.current);
    timer.current = setTimeout(() => setNotice(""), 4500);
  }, []);
  const reload = useCallback(async (background = false) => {
    const ticket = ++loadTicket.current;
    if (!background) {
      setLoading(true);
      setLoadError("");
      ownerRef.current = null;
    }
    if (!user) {
      setLoading(false);
      return;
    }
    try {
      let next: UserData;
      if (API_MODE === "api") {
        next = await loadPageData(page);
        if (
          !next.profile ||
          !Array.isArray(next.foods) ||
          !Array.isArray(next.plans)
        )
          throw new Error("Dữ liệu backend không đúng hợp đồng tích hợp.");
      } else {
        const saved = localStorage.getItem(
          `nutriguide.workspace.v1.${user.id}`,
        );
        next = saved ? JSON.parse(saved) : createDemoData(user.name);
        if (
          !next.profile ||
          !Array.isArray(next.foods) ||
          !Array.isArray(next.plans)
        )
          throw new Error(
            "Dữ liệu lưu trên trình duyệt không hợp lệ. Bạn có thể xóa dữ liệu trang này để khởi tạo lại.",
          );
      }
      if (ticket !== loadTicket.current) return;
      setUserData(next);
      ownerRef.current = user.id;
    } catch (error) {
      if (background) throw error;
      if (ticket === loadTicket.current) setLoadError((error as Error).message);
    } finally {
      if (ticket === loadTicket.current) {
        setLoadedPage(page);
        setLoading(false);
      }
    }
  }, [user, page]);
  useEffect(() => {
    void reload();
  }, [reload]);
  useEffect(() => {
    if (
      API_MODE !== "demo" ||
      !user ||
      loading ||
      loadError ||
      ownerRef.current !== user.id
    )
      return;
    try {
      localStorage.setItem(
        `nutriguide.workspace.v1.${user.id}`,
        JSON.stringify(userData),
      );
      setStorageError("");
    } catch {
      setStorageError(
        "Trình duyệt không lưu được dữ liệu. Dữ liệu hiện tại sẽ mất khi tải lại.",
      );
    }
  }, [userData, user, loading, loadError]);
  const login = (next: User) => {
    sessionStorage.setItem("nutriguide.user", JSON.stringify(next));
    setUser(next);
  };
  const logout = useCallback(async () => {
    try {
      if (API_MODE === "api") await logoutAccount();
    } catch (error) {
      toast((error as Error).message);
      throw error;
    }
    loadTicket.current++;
    ownerRef.current = null;
    sessionStorage.removeItem("nutriguide.user");
    sessionStorage.removeItem("nutriguide.token");
    setUser(null);
    setUserData(API_MODE === "api" ? emptyUserData() : createDemoData());
  }, [toast]);
  useEffect(() => {
    const unauthorized = () => {
      if (API_MODE === "demo") {
        toast(
          "Dịch vụ thật yêu cầu đăng nhập. Kiểm tra cấu hình kết nối chatbot.",
        );
        return;
      }
      loadTicket.current++;
      ownerRef.current = null;
      sessionStorage.removeItem("nutriguide.user");
      sessionStorage.removeItem("nutriguide.token");
      setUser(null);
      setUserData(emptyUserData());
      toast("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
    };
    window.addEventListener("nutriguide:unauthorized", unauthorized);
    return () =>
      window.removeEventListener("nutriguide:unauthorized", unauthorized);
  }, [toast]);
  return (
    <AppContext.Provider
      value={{
        user,
        userData,
        loading: loading || (!!user && loadedPage !== page),
        loadError,
        storageError,
        login,
        logout,
        reload,
        update: (fn) => {
          const expectedOwner = user?.id;
          setUserData((current) =>
            expectedOwner && ownerRef.current === expectedOwner
              ? fn(current)
              : current,
          );
        },
        toast,
        notice,
      }}
    >
      {children}
    </AppContext.Provider>
  );
}
export function useApp() {
  const value = useContext(AppContext);
  if (!value) throw new Error("Missing UserDataProvider");
  return value;
}
