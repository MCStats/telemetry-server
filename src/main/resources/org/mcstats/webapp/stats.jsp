<%@ page import="org.mcstats.MCStats" %>
<%@ page import="org.mcstats.DatabaseQueue" %>
<% MCStats mcstats = MCStats.getInstance(); %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">

    <head>
        <meta charset="utf-8" />
        <title>Plugin Metrics</title>
        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
        <meta name="description" content="" />
        <meta name="author" content="Tyler Blair <hidendra@griefcraft.com>" />

        <link href="http://static.mcstats.org/css/bootstrap.css" rel="stylesheet" />
        <link href="http://static.mcstats.org/css/bootstrap-responsive.css" rel="stylesheet" />
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
        <div class="container-fluid">

            <div class="row">

                <div class="span4 offset6" style="text-align: center">

                    <h2> Queue size: <%= mcstats.getDatabaseQueue().size() %> </h2> <br/>

                    <table class="table table-striped table-bordered">

                        <thead>
                        <tr> <th style="width: 10px; text-align: center;"> Worker </th> <th> Status </th> <th> Runtime </th></tr>
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

            </div>

         </div>

    </body>
</html>