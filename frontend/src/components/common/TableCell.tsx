import type { ReactNode } from "react";

export function TableCell({
  label,
  children,
}: {
  label: string;
  children: ReactNode;
}) {
  return (
    <td role="cell">
      <span className="table-cell-label" aria-hidden="true">
        {label}
      </span>
      <div>{children}</div>
    </td>
  );
}
