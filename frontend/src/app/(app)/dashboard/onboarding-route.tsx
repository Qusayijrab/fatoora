import { ArrowRight, Check } from "lucide-react";
import Link from "next/link";
import { cn } from "@/lib/utils";

export type RouteStep = {
  key: string;
  title: string;
  detail: string;
  href: string;
  cta: string;
  done: boolean;
};

/**
 * "From work to paid": the whole product as one route. Finished legs are stamped,
 * the next leg is lit in saffron, later legs wait. The order is the workflow, so
 * the sequence itself carries meaning.
 */
export function OnboardingRoute({ steps }: { steps: RouteStep[] }) {
  const nextIndex = steps.findIndex((s) => !s.done);
  const doneCount = steps.filter((s) => s.done).length;

  return (
    <section aria-labelledby="route-title" className="@container overflow-hidden rounded-lg border bg-card">
      <div className="flex flex-wrap items-baseline justify-between gap-2 border-b px-5 py-4 sm:px-6">
        <h2 id="route-title" className="text-base font-semibold">
          From work to paid
        </h2>
        <p className="figures text-sm text-muted-foreground">
          {doneCount} of {steps.length} done
        </p>
      </div>
      <ol className="grid divide-y @min-[52rem]:grid-cols-7 @min-[52rem]:divide-x @min-[52rem]:divide-y-0">
        {steps.map((step, i) => {
          const isNext = i === nextIndex;
          return (
            <li
              key={step.key}
              data-testid={`route-${step.key}`}
              data-done={step.done}
              className={cn(
                "relative flex gap-3 px-5 py-4 @min-[52rem]:flex-col @min-[52rem]:gap-2 @min-[52rem]:px-4 @min-[52rem]:py-5",
                isNext && "bg-saffron/12",
              )}
            >
              {isNext && <span aria-hidden className="absolute inset-y-0 start-0 w-[3px] bg-saffron @min-[52rem]:inset-x-0 @min-[52rem]:bottom-auto @min-[52rem]:h-[3px] @min-[52rem]:w-auto" />}
              <span
                className={cn(
                  "figures grid size-7 shrink-0 place-items-center rounded-full border text-xs font-semibold",
                  step.done && "border-primary bg-primary text-primary-foreground",
                  isNext && "border-saffron-foreground/40 bg-saffron text-saffron-foreground",
                  !step.done && !isNext && "text-muted-foreground",
                )}
              >
                {step.done ? <Check className="size-3.5" strokeWidth={3} /> : i + 1}
              </span>
              <div className="min-w-0 flex-1">
                <p className={cn("text-sm font-medium", !step.done && !isNext && "text-muted-foreground")}>{step.title}</p>
                <p className="mt-0.5 text-xs leading-relaxed text-muted-foreground">{step.detail}</p>
                {!step.done && (
                  <Link
                    href={step.href}
                    className={cn(
                      "mt-2 inline-flex items-center gap-1 text-xs font-medium",
                      isNext ? "text-foreground underline decoration-saffron decoration-2" : "text-primary hover:underline",
                    )}
                  >
                    {step.cta}
                    <ArrowRight className="size-3 rtl:rotate-180" />
                  </Link>
                )}
              </div>
            </li>
          );
        })}
      </ol>
    </section>
  );
}
