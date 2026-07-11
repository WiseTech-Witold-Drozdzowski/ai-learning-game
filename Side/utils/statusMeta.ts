import {
  APPLICATION_STATUSES,
  STATUS_LABELS,
  type ApplicationStatus,
} from '~/types/application'

/** Nuxt UI badge/select color per status — the app's only bit of status theming. */
export const STATUS_COLORS: Record<
  ApplicationStatus,
  'neutral' | 'info' | 'primary' | 'warning' | 'success' | 'error'
> = {
  SAVED: 'neutral',
  APPLIED: 'info',
  PHONE_SCREEN: 'primary',
  INTERVIEW: 'warning',
  OFFER: 'success',
  REJECTED: 'error',
  WITHDRAWN: 'neutral',
}

/**
 * Literal Tailwind background classes for the small status dot. Kept as full
 * static strings (not interpolated) so Tailwind's scanner keeps them.
 */
export const STATUS_DOT: Record<ApplicationStatus, string> = {
  SAVED: 'bg-gray-400',
  APPLIED: 'bg-sky-500',
  PHONE_SCREEN: 'bg-indigo-500',
  INTERVIEW: 'bg-amber-500',
  OFFER: 'bg-emerald-500',
  REJECTED: 'bg-rose-500',
  WITHDRAWN: 'bg-gray-400',
}

/** `{ label, value }` items for status <select> controls. */
export function statusSelectItems() {
  return APPLICATION_STATUSES.map((status) => ({
    label: STATUS_LABELS[status],
    value: status,
  }))
}

/** Same as {@link statusSelectItems} but with an "All statuses" first option. */
export function statusFilterItems() {
  return [{ label: 'All statuses', value: 'ALL' as const }, ...statusSelectItems()]
}
