package org.jboss.arquillian.container.glassfish.client;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Result from mutating endpoints (deploy/undeploy).
 * Validates the exit_code field from the XML envelope.
 */
public record ActionResult(String exitCode, String message) {

    private static final Logger log = Logger.getLogger(ActionResult.class.getName());

    public static ActionResult fromParsedMap(Map<String, Object> map) {
        String exitCode = (String) map.get("exit_code");
        String message = (String) map.get("message");
        validateExitCode(exitCode, message);
        return new ActionResult(exitCode != null ? exitCode : "SUCCESS", message);
    }

    private static void validateExitCode(String exitCode, String message) {
        if (exitCode == null || "SUCCESS".equals(exitCode)) {
            return;
        }
        if ("WARNING".equals(exitCode)) {
            log.warning("Deployment resulted in a warning: " + message);
            return;
        }
        throw new GlassFishClientException("exit_code: " + exitCode + ", message: " + message);
    }
}
