package com.fatoora.invoicing.jobs;

import java.time.Clock;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * The schedules. Each job runs on every instance's timer, but ShedLock lets only one
 * instance at a time hold its lock row, so work is never done twice in parallel. Jobs
 * run hourly because every organization has its own midnight (Amman, Riyadh, Dubai...).
 * Disabled with app.jobs.enabled=false (tests call {@link JobsService} directly).
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT15M")
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);

    private final JobsService jobs;
    private final Clock clock;

    public ScheduledJobs(JobsService jobs, Clock clock) {
        this.jobs = jobs;
        this.clock = clock;
    }

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }

    @Scheduled(cron = "${app.jobs.recurring-cron:0 */15 * * * *}")
    @SchedulerLock(name = "recurring-invoices", lockAtLeastFor = "PT30S")
    public void recurringInvoices() {
        int created = jobs.runRecurring(clock.instant());
        if (created > 0) {
            log.info("Recurring job created {} invoice(s)", created);
        }
    }

    @Scheduled(cron = "${app.jobs.status-cron:0 5 * * * *}")
    @SchedulerLock(name = "invoice-status", lockAtLeastFor = "PT30S")
    public void statusesAndReminders() {
        var now = clock.instant();
        int overdue = jobs.markOverdue(now);
        int expired = jobs.expireQuotes(now);
        int reminders = jobs.sendReminders(now);
        if (overdue + expired + reminders > 0) {
            log.info("Status job: {} overdue, {} quotes expired, {} reminders sent", overdue, expired, reminders);
        }
    }
}
