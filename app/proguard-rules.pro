# Add project specific ProGuard rules here.

# Accessibility service is bound by the system via the manifest.
-keep class com.example.usagetracker.service.ContentDetectionService { *; }

# WorkManager instantiates workers reflectively by class name.
-keep class com.example.usagetracker.tracking.UsagePollWorker { *; }
-keep class com.example.usagetracker.endofday.EndOfDayWorker { *; }
-keep class com.example.usagetracker.retention.RetentionCleanupWorker { *; }
-keep class com.example.usagetracker.notifications.TaskReminderWorker { *; }
