package com.example.tasks.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Looks for newly overdue tasks every {@code tasks.overdue.check-interval} (1 minute by default). */
@Component
@ConditionalOnProperty(name = "tasks.overdue.scheduler-enabled", havingValue = "true", matchIfMissing = true)
class OverdueCheckScheduler {

	private static final Logger log = LoggerFactory.getLogger(OverdueCheckScheduler.class);

	private final OverdueTaskNotifier notifier;

	OverdueCheckScheduler(OverdueTaskNotifier notifier) {
		this.notifier = notifier;
	}

	@Scheduled(fixedDelayString = "${tasks.overdue.check-interval:PT1M}", initialDelayString = "${tasks.overdue.initial-delay:PT10S}")
	void checkOverdueTasks() {
		var notified = notifier.notifyOverdueTasks();
		if (notified > 0) {
			log.info("Notified {} overdue task(s)", notified);
		}
	}
}
