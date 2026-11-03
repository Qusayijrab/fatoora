"use client";

import { useEffect, useRef } from "react";
import { toast } from "sonner";
import type { FormState } from "@/lib/types";

/**
 * Shows a toast once per finished Server Action: success text when `ok`, the backend's
 * problem detail when it failed. Field errors stay inline next to their inputs.
 */
export function useActionFeedback(state: FormState, success?: string) {
  const last = useRef<FormState>(state);
  useEffect(() => {
    if (state === last.current) return;
    last.current = state;
    if (state.ok && success) toast.success(success);
    else if (state.error && !state.fieldErrors) toast.error(state.error);
  }, [state, success]);
}
