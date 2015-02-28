package org.mcstats.handler;

public enum ResponseType {

    /**
     * Tell the server everything is OK
     */
    OK,

    /**
     * Tell the server it was the first request sent in the last 30 minutes
     */
    OK_FIRST_REQUEST,

    /**
     * Tell the server to regenerate its uuid
     */
    OK_REGENERATE_GUID,

    /**
     * An error occurred during the request
     */
    ERROR

}
