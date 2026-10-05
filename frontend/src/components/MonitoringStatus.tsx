interface MonitoringStatusProps {
  message: string | null;
}

export function MonitoringStatus({ message }: MonitoringStatusProps) {
  return (
    <p className="status" role="status" aria-live="polite">
      {message ?? ""}
    </p>
  );
}
