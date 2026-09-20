package com.takeoff.backend.service;

import org.springframework.core.io.Resource;

/** A stored document ready to stream to an authorised caller. */
public record DocumentDownload(Resource resource, String contentType, String filename) {
}
