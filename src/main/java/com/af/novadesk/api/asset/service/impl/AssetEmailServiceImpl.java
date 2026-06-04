package com.af.novadesk.api.asset.service.impl;

import com.af.novadesk.api.asset.entity.AssetAssignment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Sends asset-related email notifications (LLR-AST-02.2).
 *
 * <p><b>NOTE:</b> Email sending is temporarily disabled — the mail module is under
 * active development by another team member. The acknowledgment token is still generated
 * and stored; sending will be re-enabled once JavaMailSender is wired up.</p>
 */
@Slf4j
@Service
public class AssetEmailServiceImpl {

    /**
     * Sends the assignment acknowledgment email to the employee.
     *
     * <p>Currently a no-op — email sending is commented out pending mail module completion.
     * The token is already persisted on the assignment record.</p>
     */
    public void sendAcknowledgmentEmail(AssetAssignment assignment,
                                        String employeeEmail,
                                        String employeeDisplayName) {
        // TODO: re-enable once JavaMailSender is configured in all environments
        // (tracked by the mail-module developer — do not remove this method)
        log.warn("[MAIL-PENDING] Acknowledgment email not sent for assignment {} — " +
                 "mail service not yet configured. Employee: {} <{}>. " +
                 "Token is stored and can be delivered manually or via the acknowledgment endpoint.",
                 assignment.getId(), employeeDisplayName, employeeEmail);

        /*
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
            log.error("Failed to send acknowledgment email for assignment {}: {}", assignment.getId(), e.getMessage());
        }
        */
    }
}
