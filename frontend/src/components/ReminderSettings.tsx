import { useEffect, useState, type FormEvent } from "react";
import { fetchReminderPreference, saveReminderPreference } from "../api/reminderApi.ts";

export function ReminderSettings({ active }: { active: boolean }) {
  const [enabled, setEnabled] = useState(true);
  const [reminderTime, setReminderTime] = useState("08:00");
  const [timezone, setTimezone] = useState("");
  const [ready, setReady] = useState(false);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    if (!active) return;
    let cancelled = false;
    void (async () => {
      try {
        const preference = await fetchReminderPreference();
        if (cancelled) return;
        setEnabled(preference.enabled);
        setReminderTime(preference.reminderTime);
        setTimezone(preference.timezone);
        setReady(true);
      } catch {
        if (!cancelled) setReady(false);
      }
    })();
    return () => { cancelled = true; };
  }, [active]);

  if (!ready) return null;

  async function save(event: FormEvent) {
    event.preventDefault();
    const time = reminderTime.slice(0, 5);
    if (!/^\d{2}:\d{2}$/.test(time)) {
      setNotice("Choose a valid reminder time.");
      return;
    }
    setSaving(true);
    setNotice(null);
    try {
      const saved = await saveReminderPreference(enabled, time);
      setEnabled(saved.enabled);
      setReminderTime(saved.reminderTime);
      setTimezone(saved.timezone);
      setNotice("Saved.");
    } catch {
      setNotice("We couldn't save your reminder settings. Please try again.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="history-card reminder-settings" aria-labelledby="daily-reminder-heading">
      <h2 id="daily-reminder-heading">Daily Reminder</h2>
      <form onSubmit={(event) => void save(event)}>
        <label className="reminder-toggle">
          <input
            type="checkbox"
            checked={enabled}
            onChange={(event) => setEnabled(event.target.checked)}
          />
          Remind me to complete my daily check-in
        </label>
        <label htmlFor="reminder-time">Reminder time</label>
        <input
          id="reminder-time"
          type="time"
          value={reminderTime}
          onChange={(event) => setReminderTime(event.target.value)}
        />
        <p>Timezone: {timezone}</p>
        <p className="muted">CareVoice uses your profile timezone for reminder scheduling.</p>
        <button type="submit" className="secondary" disabled={saving}>Save</button>
        {notice ? <p>{notice}</p> : null}
      </form>
    </section>
  );
}
