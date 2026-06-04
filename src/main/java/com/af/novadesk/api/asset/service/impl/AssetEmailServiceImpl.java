package com.af.novadesk.api.asset.service.impl;

import com.af.novadesk.api.asset.config.AssetProperties;
import com.af.novadesk.api.asset.entity.Asset;
import com.af.novadesk.api.asset.entity.AssetAssignment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Sends asset-related email notifications (LLR-AST-02.2).
 *
 * <p>All sends are {@code @Async} — email delivery should never block a transaction.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetEmailServiceImpl {

    private final JavaMailSender   mailSender;
    private final AssetProperties  assetProperties;

    /**
     * Sends the assignment acknowledgment email to the employee.
     * Includes the one-time token link valid for 7 days.
     *
     * @param assignment the assignment record (must have token + employee email)
     * @param employeeEmail the employee's email address
     * @param employeeDisplayName the employee's display name
     */
    @Async
    public void sendAcknowledgmentEmail(AssetAssignment assignment,
                                        String employeeEmail,
                                        String employeeDisplayName) {
        Asset asset = assignment.getAsset();
        String link = assetProperties.acknowledgmentBaseUrl() + "/" + assignment.getAcknowledgmentToken();

        String subject = "Action Required: Please acknowledge receipt of " + asset.getAssetType();
        String body = buildAcknowledgmentHtml(employeeDisplayName, asset, assignment, link);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(employeeEmail);
            helper.setSubject(subject);
            helper.setText(body, true);
            mailSender.send(message);
            log.info("Acknowledgment email sent to {} for assignment {}", employeeEmail, assignment.getId());
        } catch (MessagingException e) {
            // Non-fatal — assignment is still created; IT admin can resend manually
            log.error("Failed to send acknowledgment email for assignment {}: {}", assignment.getId(), e.getMessage());
        }
    }

    // ── Template ──────────────────────────────────────────────────────────────

    private String buildAcknowledgmentHtml(String name, Asset asset,
                                           AssetAssignment assignment, String link) {
        return """
            <html><body style="font-family: sans-serif; color: #1a1a2e; max-width: 600px; margin: 0 auto;">
              <div style="background: #1a1a2e; color: white; padding: 24px 32px; border-radius: 8px 8px 0 0;">
                <h2 style="margin: 0;">Asset Receipt Acknowledgment</h2>
              </div>
              <div style="padding: 32px; border: 1px solid #e2e8f0; border-top: none; border-radius: 0 0 8px 8px;">
                <p>Hi <strong>%s</strong>,</p>
                <p>You have been assigned the following asset. Please acknowledge receipt by clicking the button below.</p>
                <table style="width: 100%%; border-collapse: collapse; margin: 20px 0;">
                  <tr><td style="padding: 8px; background: #f8fafc; font-weight: 600;">Asset Type</td>
                      <td style="padding: 8px;">%s</td></tr>
                  <tr><td style="padding: 8px; background: #f8fafc; font-weight: 600;">Serial Number</td>
                      <td style="padding: 8px; font-family: monospace;">%s</td></tr>
                  <tr><td style="padding: 8px; background: #f8fafc; font-weight: 600;">Assignment Date</td>
                      <td style="padding: 8px;">%s</td></tr>
                  <tr><td style="padding: 8px; background: #f8fafc; font-weight: 600;">Condition</td>
                      <td style="padding: 8px;">%s</td></tr>
                </table>
                <div style="text-align: center; margin: 32px 0;">
                  <a href="%s" style="background: #2563eb; color: white; padding: 14px 32px;
                     border-radius: 8px; text-decoration: none; font-weight: 600; font-size: 16px;">
                    I acknowledge receipt of this asset
                  </a>
                </div>
                <p style="color: #64748b; font-size: 13px;">This link expires in 7 days.
                   If you did not receive this asset, please contact IT immediately.</p>
              </div>
            </html>
            """.formatted(
                name,
                asset.getAssetType(),
                asset.getSerialNumber(),
                assignment.getAssignmentDate(),
                assignment.getConditionAtAssignment().name(),
                link
        );
    }
}
