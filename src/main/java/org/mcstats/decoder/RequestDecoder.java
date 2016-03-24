package org.mcstats.decoder;

import org.eclipse.jetty.server.Request;

import java.io.IOException;

public interface RequestDecoder {

    /**
     * Decode a request
     *
     * @param request
     * @return
     * @throws IOException
     */
    DecodedRequest decode(Request request) throws IOException;

}
