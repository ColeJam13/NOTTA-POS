package com.notapos.dto;

import com.notapos.service.NotificationService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A message written to the floor: POST /api/notifications/messages.
 *
 * Only the words. Who wrote it is the signed-in person, taken from the sign-in
 * and never from the request: a request cannot send a message as someone else.
 *
 * @param text The message, 200 characters at most, not empty
 */
public record MessageRequest(
        @NotBlank(message = "Write a message first")
        @Size(max = NotificationService.MESSAGE_MAX_LENGTH, message = "A message is 200 characters at most")
        String text) {
}
