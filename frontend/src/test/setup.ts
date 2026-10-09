import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";
import { resetCsrf } from "../api/monitoringApi.ts";

afterEach(() => {
  resetCsrf();
  cleanup();
});
