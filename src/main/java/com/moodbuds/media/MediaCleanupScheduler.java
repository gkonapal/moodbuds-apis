package com.moodbuds.media;

import java.time.Duration;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled tasks for media storage cleanup.
 * Removes orphaned temporary files that were never finalized.
 */
@Component
public class MediaCleanupScheduler {
    private final MediaService mediaService;

    public MediaCleanupScheduler(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    /**
     * Cleanup orphaned temporary files older than 24 hours.
     * Runs daily at 2 AM (UTC).
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    public void cleanupOrphanedTempFiles() {
        System.out.println("Starting cleanup of orphaned temporary media files older than 24 hours...");
        mediaService.cleanupOrphanedTempFiles(Duration.ofHours(24));
        System.out.println("Orphaned media file cleanup completed.");
    }
}
