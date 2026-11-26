import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { AppRouter } from "./routes/AppRouter";
import { UserDataProvider } from "./store/UserDataProvider";
import "./styles.css";
ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <BrowserRouter>
      <UserDataProvider>
        <AppRouter />
      </UserDataProvider>
    </BrowserRouter>
  </React.StrictMode>,
);
