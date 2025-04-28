package org.example.utils;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class ApiResponse
{

    /**
     * Sends a successful HTTP JSON response.
     *
     * @param ctx         The routing context.
     * @param data        The response payload (can be null).
     * @param message     A human-readable message.
     * @param statusCode  The HTTP status code to send.
     */

    public static void success(RoutingContext ctx, Object data, String message, int statusCode)
    {
        var response = new JsonObject()
                .put("status", "success")
                .put("message", message != null ? message : "Operation successful");

        if (data != null)
        {
            response.put("data", data);
        }

        ctx.response()
                .setStatusCode(statusCode)
                .putHeader("Content-Type", "application/json")
                .end(response.encode());
    }

    /**
     * Sends an error HTTP JSON response with optional error details.
     *
     * @param ctx           The routing context.
     * @param message       A human-readable error message.
     * @param errorDetails  Additional error information (can be null).
     * @param statusCode    The HTTP status code to send.
     */
    public static void error(RoutingContext ctx, String message, Object errorDetails, int statusCode)
    {
        var response = new JsonObject()
                .put("status", "error")
                .put("message", message != null ? message : "Operation failed");

        if (errorDetails != null)
        {
            response.put("error", errorDetails);
        }

        ctx.response()
                .setStatusCode(statusCode)
                .putHeader("Content-Type", "application/json")
                .end(response.encode());
    }

    /**
     * Sends a simple error HTTP JSON response without error details.
     *
     * @param ctx          The routing context.
     * @param message      A human-readable error message.
     * @param statusCode   The HTTP status code to send.
     */
    public static void error(RoutingContext ctx, String message, int statusCode)
    {
        error(ctx, message, null, statusCode);
    }
}
