package com.example.schemasyncbot.service;

import com.example.schemasyncbot.model.SessionData;
import com.example.schemasyncbot.utils.MessageTemplates;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.SendDocument;
import com.pengrad.telegrambot.request.SendMessage;

import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class JenkinsPollingService implements IJenkinsPollingService {
    private final TelegramBot bot;
    private final JenkinsService jenkins;
    private final SchemaSyncService schemaSync;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public JenkinsPollingService(TelegramBot bot, JenkinsService jenkins, SchemaSyncService schemaSync) {
        this.bot = bot;
        this.jenkins = jenkins;
        this.schemaSync = schemaSync;
    }

    // Add methods for polling Jenkins and sending results here
    // Example:
    // public void pollForDiffArtifact(Long chatId, String jobName, SessionData session) { ... }

    public void pollJenkinsForDiffArtifact(Long chatId, String jobName, SessionData session) {
        jenkins.getLastBuildNumber(jobName).subscribe(buildNumber -> {
            session.setLastBuildNumber(buildNumber);
            final int[] attempts = {0};
            Runnable pollTask = new Runnable() {
                @Override
                public void run() {
                    jenkins.downloadArtifact(jobName, buildNumber, "schema-diff-.*\\.xml")
                        .subscribe(file -> {
                            if (file != null && file.exists()) {
                                session.setDiffFile(file);
                                bot.execute(new SendMessage(chatId, "Here is the diff file. Review and approve to apply."));
                                InlineKeyboardMarkup diffKb2 = new InlineKeyboardMarkup(
                                        new InlineKeyboardButton("✅ Approve").callbackData("approve_diff"),
                                        new InlineKeyboardButton("❌ Cancel").callbackData("cancel_diff"));
                                bot.execute(new SendDocument(chatId, file)
                                    .caption("Review diff file below.")
                                    .replyMarkup(diffKb2));

                                // Use the latest diff ID instead of trying to extract from filename
                                schemaSync.getLatestDiffId().subscribe(latestId -> {
                                    if (latestId == null) {
                                        bot.execute(new SendMessage(chatId, "❌ No diffs found in database."));
                                        return;
                                    }
                                    schemaSync.fetchParsedDiff(latestId).subscribe(parsed -> {
                                        session.setParsedDiff(parsed); // store in session for callback use
                                    }, error -> {
                                        try {
                                            bot.execute(new SendMessage(chatId, "📋 XML file received. Parsed summary not available."));
                                        } catch (Exception e) {
                                            bot.execute(new SendMessage(chatId, "❌ Error parsing XML: " + e.getMessage()));
                                        }
                                    });
                                }, error -> {
                                    bot.execute(new SendMessage(chatId, "❌ Failed to get latest diff ID: " + error.getMessage()));
                                });
                            } else if (attempts[0] < 12) { // 12 attempts = 2 minutes
                                attempts[0]++;
                                scheduler.schedule(this, 10, TimeUnit.SECONDS);
                            } else {
                                bot.execute(new SendMessage(chatId, "❌ Timed out waiting for diff artifact from Jenkins."));
                                // Fallback: try direct diff generation (optional, can be handled by caller)
                            }
                        }, err -> {
                            if (attempts[0] < 12) {
                                attempts[0]++;
                                scheduler.schedule(this, 10, TimeUnit.SECONDS);
                            } else {
                                bot.execute(new SendMessage(chatId, "❌ Timed out waiting for diff artifact from Jenkins."));
                                // Fallback: try direct diff generation (optional, can be handled by caller)
                            }
                        });
                }
            };
            scheduler.schedule(pollTask, 10, TimeUnit.SECONDS);
        }, error -> {
            bot.execute(new SendMessage(chatId, "❌ Failed to get build number: " + MessageTemplates.safeErrorMessage(error)));
            // Fallback: try direct diff generation (optional, can be handled by caller)
        });
    }
} 