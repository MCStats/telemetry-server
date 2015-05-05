package org.mcstats.decoder;

import org.eclipse.jetty.server.Request;
import org.mcstats.model.Plugin;

import java.io.IOException;

public interface RequestDecoder {

    /**
     * Decode a request
     *
     * @param plugin
     * @param request
     * @return
     * @throws IOException
     */
    DecodedRequest decode(Plugin plugin, Request request) throws IOException;

}
