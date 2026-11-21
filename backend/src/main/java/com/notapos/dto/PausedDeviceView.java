package com.notapos.dto;

/**
 * One terminal where sign-in is paused after too many wrong PINs, as a manager
 * sees it on the Staff screen (GET /api/staff/paused-devices).
 *
 * "Device" is the address the terminal's requests come from: it is the only
 * thing the backend knows a terminal by (see SignInGuard).
 *
 * @param device      The terminal's address, e.g. "192.168.1.21"
 * @param secondsLeft How long until the pause ends by itself, rounded up
 * @param thisDevice  True when it is the terminal the manager is asking from
 */
public record PausedDeviceView(String device, long secondsLeft, boolean thisDevice) {
}
