package org.mcstats.handler;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.ByteArrayISO8859Writer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;

public class BlackholeHandler extends AbstractHandler {

    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        request.setCharacterEncoding("UTF-8");
        response.setHeader("Connection", "close");
        baseRequest.setHandled(true);
        response.setStatus(200);
        response.setContentType("text/plain");
        ByteArrayISO8859Writer writer = new ByteArrayISO8859Writer(1500);
        writer.write("OK");
        writer.flush();

        response.setContentLength(writer.size());

        OutputStream outputStream = response.getOutputStream();
        writer.writeTo(outputStream);

        outputStream.close();
        writer.close();
        baseRequest.getConnection().getEndPoint().close();
    }

}
