<%@ taglib prefix="fmt" uri="http://java.sun.com/jstl/fmt" %>
<%@ page import="org.mcstats.DatabaseQueue" %>
<%@ page import="org.mcstats.MCStats" %>
<%@ page import="org.mcstats.util.TimeUtils" %>
<%@ page import="java.text.DecimalFormat" %>
<%@ page import="org.mcstats.db.MySQLDatabase" %>
<%
    MCStats mcstats = MCStats.getInstance();
    long requests = mcstats.incrementAndGetRequests();
    DecimalFormat numberFormatter = new DecimalFormat( "###,###,###,###" );
%>
<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="utf-8" />
    <title>MCStats :: Backend Status</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <meta name="description" content="" />
    <meta name="author" content="Tyler Blair <hidendra@griefcraft.com>" />

    <meta name="viewport" content="width=device-width">

    <!-- css -->
    <link href="http://static.mcstats.org/css/libraries/bootstrap/bootstrap.min.css" rel="stylesheet"/>
    <link href="http://static.mcstats.org/css/libraries/font-awesome/font-awesome.min.css" rel="stylesheet"/>
    <link href="http://static.mcstats.org/css/libraries/template/template.min.css" rel="stylesheet"/>
    <link href="http://static.mcstats.org/css/libraries/template/template.blue.min.css" rel="stylesheet"/>
    <link href="http://static.mcstats.org/css/libraries/jquery/jquery.jscrollpane.css" rel="stylesheet"/>
    <link href="http://static.mcstats.org/css/libraries/jquery/typeahead.js-bootstrap.css" rel="stylesheet"/>
    <link href="http://static.mcstats.org/css/libraries/famfamfam/fam-icons.css" rel="stylesheet"/>

    <!-- core libs -->
    <script src="http://static.mcstats.org/javascript/libraries/jquery/jquery.min.js" type="text/javascript"></script>
    <script src="http://static.mcstats.org/javascript/libraries/jquery/jquery.pjax.js" type="text/javascript"></script>
    <script src="http://static.mcstats.org/javascript/libraries/jquery/jquery.jpanelmenu.min.js" type="text/javascript"></script>
    <script src="http://static.mcstats.org/javascript/libraries/jquery/jquery.nicescroll.min.js" type="text/javascript"></script>
    <script src="http://static.mcstats.org/javascript/libraries/bootstrap/bootstrap.min.js" type="text/javascript"></script>
    <script src="http://static.mcstats.org/javascript/libraries/bootstrap/tooltip.js" type="text/javascript"></script>
    <script src="http://static.mcstats.org/javascript/libraries/bootstrap/typeahead.min.js" type="text/javascript"></script>
    <script src="http://static.mcstats.org/javascript/libraries/peity/jquery.peity.js" type="text/javascript"></script>
    <script src="http://static.mcstats.org/javascript/libraries/template/template.min.js" type="text/javascript"></script>

    <!-- charting -->
    <script src="http://static.mcstats.org/javascript/libraries/highcharts/highstock.js" type="text/javascript"></script>
    <script src="http://static.mcstats.org/javascript/libraries/highcharts/themes/simplex.js" type="text/javascript"></script>
    <script src="http://static.mcstats.org/javascript/libraries/highcharts/exporting.js" type="text/javascript"></script>

    <!-- mcstats -->
    <script src="http://static.mcstats.org/javascript/mcstats.js" type="text/javascript"></script>

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
<div id="header">
    <h1><a href="/">MCStats / Plugin Metrics</a></h1>
</div>

<div id="search">
    <form action="" method="post" onsubmit="window.location='/plugin/' + $('#goto').val(); return false;">
        <input type="text" id="goto" placeholder="Go to Plugin" autocomplete="off"/><button type="submit" class="tip-right" title="Go"><i class="icon-search"></i></button>
    </form>
</div>

<div id="sidebar">
    <ul>
        <li><a href="/"><i class="icon icon-home"></i> <span>Homepage</span></a>
        </li>
        <li><a href="/plugin-list/"><i class="icon icon-list-alt"></i> <span>Plugin List</span></a></li>
        <li><a href="/global/"><i class="icon icon-signal"></i> <span>Global Statistics</span></a></li>
        <li class="active"><a href="/status/"><i class="icon icon-retweet"></i> <span>Backend Status</span></a></li>
        <li class="submenu">
            <a href="#"><i class="icon icon-wrench"></i> <span>Plugin Admin</span> <span class="label">2</span></a>
            <ul>
                <li><a href="/admin/">Admin home</a></li>
                <li><a href="/admin/add-plugin/">Add a plugin</a></li>
            </ul>
        </li>

        <li>
            <a><span>A very special thanks to the MCStats sponsors:</span></a>

            <ol style="padding: 0; margin: 0;" class="sponsors">
                <li><a href="http://buycraft.net" target="_blank"><img src="http://static.mcstats.org/img/sponsors/buycraft.png" width="210px" style="padding-left: 10px" /></a></li>
                <li><a href="http://avnk.net" target="_blank"><img src="http://static.mcstats.org/img/sponsors/Avalanche-Network-v5.png" width="210px" /></a></li>
                <li><a href="https://twitter.com/VladToBeHere" target="_blank"><span style="margin-left: 15px; font-size: 24px; color: #428BCA">@VladToBeHere</span></a></li>
            </ol>
        </li>
    </ul>

</div>

<div id="content">
    <div id="content-header">
        <h1>MCStats / Plugin Metrics</h1>
    </div>

    <div id="breadcrumb">
        <a href="/" title="Home" class="tip-bottom"><i class="icon-home"></i> Home</a>
        <a href="/status/" class="current">Backend Status</a>
    </div>

    <div class="container-fluid">
        <div class="row-fluid" id="graph-generator" style="display: none">
            <div>
                <div class="alert alert-info span6" style="width: 50%; padding-bottom: 0; margin-left: 25%; text-align: center; float: left;">
                    <p>
                        <strong>INFO:</strong> Graphs are currently generating.
                    </p>
                </div>

                <div class="progress progress-striped progress-success active" style="clear: left">
                    <div class="bar" id="graph-generator-progress-bar" style="width: 0"></div>
                </div>
            </div>
        </div>

        <div class="row-fluid">

            <div class="col-xs-4 col-md-offset-2" style="text-align: center">

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

            <div class="col-xs-4" style="text-align: center">

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
                            SQL queue size
                        </td>
                        <td style="width: 100px; text-align: center;">
                            <%= numberFormatter.format(mcstats.getDatabaseQueue().size()) %>
                        </td>
                    </tr>

                    <tr>
                        <td style="width: 20px; text-align: center;">
                            Thread pool queue size
                        </td>
                        <td style="width: 100px; text-align: center;">
                            <%= numberFormatter.format(mcstats.getReportHandler().queueSize()) %>
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

                    <tr>
                        <td style="width: 20px; text-align: center;">
                            Total queries
                        </td>
                        <td style="width: 100px; text-align: center;">
                            <%= numberFormatter.format(MySQLDatabase.QUERIES) %>
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

        <div class="clearfix"></div>
    </div>
</div>

<div class="row">
    <div id="footer" class="col-xs-12">
        <p> MCStats service created & maintained by <a href="mailto:hidendra@mcstats.org">Hidendra</a>. Plugins are
            owned by their respective authors. </p>

        <p><a href="http://github.com/Hidendra/mcstats.org"><i class="icon-github"></i> github</a> | <a href="http://blog.mcstats.org"><i class="icon-quote-left"></i> blog</a> | <a href="irc://irc.esper.net/metrics"><i class="icon-comments"></i> irc.esper.net #metrics</a></p>
    </div>
</div>

</body>

</html>