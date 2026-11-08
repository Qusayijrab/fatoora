package com.fatoora.invoicing.dashboard;

import com.fatoora.invoicing.dashboard.DashboardService.Summary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService service;
    private final OverviewService overview;
    private final ForecastService forecast;

    public DashboardController(DashboardService service, OverviewService overview, ForecastService forecast) {
        this.service = service;
        this.overview = overview;
        this.forecast = forecast;
    }

    /** Money expected in over the next 12 weeks, per week, in the base currency. */
    @GetMapping("/forecast")
    public ForecastService.Forecast forecast() {
        return forecast.forecast();
    }

    /** Money totals, 12-month series, aging, top balances and recent activity, in the base currency. */
    @GetMapping("/overview")
    public OverviewService.Overview overview() {
        return overview.overview();
    }

    @GetMapping("/summary")
    public Summary summary() {
        return service.summary();
    }
}
