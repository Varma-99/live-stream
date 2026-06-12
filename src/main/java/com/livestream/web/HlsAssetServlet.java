package com.livestream.web;

import io.dropwizard.core.setup.Environment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.eclipse.jetty.servlet.DefaultServlet;

/**
 * Serves generated HLS files from disk at {@code /hls/{streamId}/index.m3u8}.
 */
public final class HlsAssetServlet {

    private HlsAssetServlet() {
    }

    public static void register(Environment environment, String hlsOutputDir) throws IOException {
        Path basePath = Paths.get(hlsOutputDir).toAbsolutePath().normalize();
        Files.createDirectories(basePath);

        var registration = environment.servlets().addServlet("hls-static", DefaultServlet.class);
        registration.addMapping("/hls/*");
        registration.setInitParameter("resourceBase", basePath.toString());
        registration.setInitParameter("dirAllowed", "false");
        registration.setInitParameter("pathInfoOnly", "true");
    }
}
