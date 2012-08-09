<%@ taglib prefix="fmt" uri="http://java.sun.com/jstl/fmt" %>
<%@ page import="org.mcstats.DatabaseQueue" %>
<%@ page import="org.mcstats.MCStats" %>
<%@ page import="org.mcstats.util.TimeUtils" %>
<%@ page import="java.text.DecimalFormat" %>
<%
    MCStats mcstats = MCStats.getInstance();
    long requests = mcstats.incrementAndGetRequests();
    DecimalFormat numberFormatter = new DecimalFormat( "###,###,###,###" );
%>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">

    <head>
        <meta charset="utf-8" />
        <title>Plugin Metrics</title>
        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
        <meta name="description" content="" />
        <meta name="author" content="Tyler Blair <hidendra@griefcraft.com>" />

        <link href="http://static.mcstats.org/css/bootstrap.min.css" rel="stylesheet" />
        <link href="http://static.mcstats.org/css/bootstrap-responsive.min.css" rel="stylesheet" />
        <link href="http://static.mcstats.org/css/ui-lightness/jquery-ui.css" rel="stylesheet" />

        <script src="http://static.mcstats.org/javascript/jquery.js" type="text/javascript"></script>
        <script src="http://static.mcstats.org/javascript/jquery.pjax.js" type="text/javascript"></script>
        <script src="http://static.mcstats.org/javascript/jquery-ui.js" type="text/javascript"></script>
        <script src="http://static.mcstats.org/javascript/main.js" type="text/javascript"></script>

        <script type="text/javascript">
            // Google analytics
            var _gaq = _gaq || [];
            _gaq.push(['_setAccount', 'UA-31036792-1']);
            _gaq.push(['_setDomainName', 'mcstats.org']);
            _gaq.push(['_setAllowLinker', true]);
            _gaq.push(['_trackPageview']);

            (function() {
                var ga = document.createElement('script'); ga.type = 'text/javascript'; ga.async = true;
                ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';
                var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(ga, s);
            })();
        </script>
    </head>

    <body>

        <br />
        <div class="container">

            <div class="row" style="text-align: center; margin-bottom: 15px;">
                <h2> Plugin Metrics Backend Status </h2>
            </div>

            <div class="row">

                <div class="span4 offset2" style="text-align: center">

                    <table class="table table-striped table-bordered">

                        <thead>
                        <tr> <th style="width: 10px; text-align: center;"> Worker </th> <th style="text-align: center;"> Status </th> <th style="text-align: center;"> Runtime </th></tr>
                        </thead>

                        <tbody>

                        <%
                            for (DatabaseQueue.QueueWorker worker : mcstats.getDatabaseQueue().getWorkers()) { %>
                        <tr>
                            <td style="width: 10px; text-align: center;">
                                <%= worker.getId() %>
                            </td>
                            <td style="width: 10px; text-align: center;">
                                <%= worker.isBusy() ? "BUSY" : "SLEEPING" %>
                            </td>
                            <td style="width: 10px; text-align: center;">
                                <%= worker.isBusy() ? ((System.currentTimeMillis() - worker.getJobStart()) + "ms") : "0ms" %>
                            </td>
                        </tr><%
                            }
                        %>

                        </tbody>

                    </table>

                </div>

                <div class="span4" style="text-align: center">

                    <table class="table table-striped table-bordered">

                        <tbody>

                        <tr>
                            <td style="width: 20px; text-align: center;">
                                Time running
                            </td>
                            <td style="width: 100px; text-align: center;">
                                <%= TimeUtils.timeToString((System.currentTimeMillis() - mcstats.getRequestCalculatorAllTime().getStart()) / 1000) %>
                            </td>
                        </tr>

                        <tr>
                            <td style="width: 20px; text-align: center;">
                                Open connections
                            </td>
                            <td style="width: 100px; text-align: center;">
                                <%= numberFormatter.format(mcstats.countOpenConnections()) %>
                            </td>
                        </tr>

                        <tr>
                            <td style="width: 20px; text-align: center;">
                                Background queue size
                            </td>
                            <td style="width: 100px; text-align: center;">
                                <%= numberFormatter.format(mcstats.getDatabaseQueue().size()) %>
                            </td>
                        </tr>

                        <tr>
                            <td style="width: 20px; text-align: center;">
                                Total requests
                            </td>
                            <td style="width: 100px; text-align: center;">
                                <%= numberFormatter.format(requests) %>
                            </td>
                        </tr>

                        <tr>
                            <td style="width: 20px; text-align: center;">
                                Requests per second
                            </td>
                            <td style="width: 100px; text-align: center;">
                                <%= numberFormatter.format(mcstats.getRequestCalculatorAllTime().calculateRequestsPerSecond()) %>
                            </td>
                        </tr>

                        </tbody>

                    </table>

                    <table class="table table-striped table-bordered">

                        <tbody>

                        <tr>
                            <td style="width: 20px; text-align: center;">
                                Servers (cached)
                            </td>
                            <td style="width: 100px; text-align: center;">
                                <%= numberFormatter.format(mcstats.getCachedServers().size()) %>
                            </td>
                        </tr>

                        <tr>
                            <td style="width: 20px; text-align: center;">
                                Plugins (cached)
                            </td>
                            <td style="width: 100px; text-align: center;">
                                <%= numberFormatter.format(mcstats.getCachedPlugins().size()) %>
                            </td>
                        </tr>

                        </tbody>

                    </table>

                </div>

            </div>

         </div>

    </body>
</html>