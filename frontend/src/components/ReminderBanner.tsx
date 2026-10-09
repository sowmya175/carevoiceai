import { useEffect, useState } from "react";
import { fetchNotifications, markNotificationRead, type InAppNotification } from "../api/reminderApi.ts";
import type { DailyCheckIn } from "../types/monitoring.ts";

const REFRESH_MS = 180_000;

export function ReminderBanner({
  active,
  today,
  busy,
  onOpen,
}: {
  active: boolean;
  today: DailyCheckIn | null;
  busy: boolean;
  onOpen: () => void;
}) {
  const [items, setItems] = useState<InAppNotification[]>([]);

  useEffect(() => {
    if (!active) return;
    let cancelled = false;
    async function load() {
      try {
        const notifications = await fetchNotifications();
        if (!cancelled) setItems(notifications);
      } catch {
        if (!cancelled) setItems([]);
      }
    }
    void load();
    const timer = window.setInterval(() => { void load(); }, REFRESH_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [active]);

  const notification = items.find((item) => !item.read);
  if (notification === undefined || today === null) return null;
  const unread = notification;

  const finished = today.status === "COMPLETED" || today.status === "READY_FOR_REVIEW";
  const continuation = unread.messageKey === "PREVIOUS_CHECK_IN_CONTINUE";
  const action = finished ? null : continuation ? "Continue Check-In" : "Start Check-In";

  async function dismiss() {
    const id = unread.id;
    setItems((current) => current.map((item) => item.id === id ? { ...item, read: true } : item));
    try {
      await markNotificationRead(id);
    } catch {
      setItems((current) => current.map((item) => item.id === id ? { ...item, read: false } : item));
    }
  }

  return (
    <section className="history-card reminder-banner" aria-label="CareVoice reminder">
      <h2>CareVoice Reminder</h2>
      <p>{unread.message}</p>
      <div className="button-row">
        {action ? (
          <button type="button" className="primary" onClick={onOpen} disabled={busy}>{action}</button>
        ) : null}
        <button type="button" className="secondary" onClick={() => void dismiss()} disabled={busy}>Dismiss</button>
      </div>
    </section>
  );
}
