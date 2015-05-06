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
    <meta charset="utf-8"/>
    <title>MCStats :: Backend Status</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
    <meta name="description" content=""/>
    <meta name="author" content="Tyler Blair <hidendra@griefcraft.com>"/>

    <meta name="viewport" content="width=device-width">

    <!-- css -->
    <link href="http://static.mcstats.org/css/libraries/bootstrap/bootstrap.min.css" rel="stylesheet"/>
    <link href="http://static.mcstats.org/css/libraries/font-awesome/font-awesome.min.css" rel="stylesheet"/>
    <link href="http://static.mcstats.org/css/libraries/template/template.min.css" rel="stylesheet"/>
    <link href="http://static.mcstats.org/css/libraries/jquery/typeahead.js-bootstrap.css" rel="stylesheet"/>
    <link href="http://static.mcstats.org/css/libraries/famfamfam/fam-icons.css" rel="stylesheet"/>

    <!-- core libs -->
    <script src="http://static.mcstats.org/javascript/libraries/jquery/jquery.min.js" type="text/javascript"></script>
    <script src="http://static.mcstats.org/javascript/libraries/jquery/jquery.pjax.js" type="text/javascript"></script>
    <script src="http://static.mcstats.org/javascript/libraries/jquery/jquery.jpanelmenu.min.js" type="text/javascript"></script>
    <script src="http://static.mcstats.org/javascript/libraries/jquery/jquery.sparkline.min.js" type="text/javascript"></script>
    <script src="http://static.mcstats.org/javascript/libraries/bootstrap/bootstrap.min.js" type="text/javascript"></script>
    <script src="http://static.mcstats.org/javascript/libraries/bootstrap/tooltip.js" type="text/javascript"></script>
    <script src="http://static.mcstats.org/javascript/libraries/bootstrap/typeahead.min.js" type="text/javascript"></script>
    <script src="http://static.mcstats.org/javascript/libraries/template/template.min.js" type="text/javascript"></script>

    <!-- charting -->
    <script src="http://static.mcstats.org/javascript/libraries/highcharts/highstock.js" type="text/javascript"></script>
    <script src="http://static.mcstats.org/javascript/libraries/highcharts/themes/simplex.js" type="text/javascript"></script>
    <script src="http://static.mcstats.org/javascript/libraries/highcharts/exporting.js" type="text/javascript"></script>

    <!-- mcstats -->
    <script src="http://static.mcstats.org/javascript/mcstats.js" type="text/javascript"></script>

    <script type='text/javascript' src='https://www.google.com/jsapi'></script>

    <script type="text/javascript">
        // Google analytics
        var _gaq = _gaq || [];
        _gaq.push(['_setAccount', 'UA-31036792-1']);
        _gaq.push(['_setDomainName', 'mcstats.org']);
        _gaq.push(['_setAllowLinker', true]);
        _gaq.push(['_trackPageview']);

        (function () {
            var ga = document.createElement('script');
            ga.type = 'text/javascript';
            ga.async = true;
            ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';
            var s = document.getElementsByTagName('script')[0];
            s.parentNode.insertBefore(ga, s);
        })();

        google.load('visualization', '1', {'packages': ['geochart']});
    </script>
</head>
<body data-color="grey" class="flat">

<div id="wrapper">
<div id="header">
    <h1><a href="/">MCStats / Plugin Metrics</a></h1>
</div>

<div id="sidebar">
    <div id="search">
        <form action="" method="post" onsubmit="window.location='/plugin/' + $('#goto').val(); return false;">
            <input type="text" id="goto" placeholder="Search plugins" autocomplete="off"/><button type="submit" class="tip-right" title="Go"><i class="icon-search"></i></button>
        </form>
    </div>

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

            <ol style="padding: 0; margin: 0;" class="sponsors hidden-xs hidden-sm">
                <li><a href="http://buycraft.net" target="_blank"><img src="http://static.mcstats.org/img/sponsors/buycraft.png" width="190px" style="padding-left: 10px" /></a></li>
            </ol>
        </li>
    </ul>

</div>

<div id="content">
    <div id="breadcrumb">
        <a href="/" title="Home"
           class="tip-bottom current"><i
                class="icon-home"></i> Home</a>
        <a href="/plugin-list/" class="current"><i class="icon-retweet"></i> Backend Status</a>
    </div>

    <div class="container-fluid" style="min-height: 550px">

        <div class="row" id="graph-generator" style="display: none">
            <div class="col-xs-12 col-lg-8 col-lg-offset-2">
                <div class="alert alert-info">
                    <p>
                        <strong>INFO:</strong> Graphs are currently generating.
                    </p>
                </div>

                <div class="progress progress-striped progress-sm active">
                    <div class="progress-bar progress-bar-dark-red" id="graph-generator-progress-bar" style="width: 0"></div>
                </div>
            </div>
        </div>

        <script type="text/javascript">
            $(document).ready(function() {
                $("#players-popover").popover();
            });
        </script>

        <div class="row">

            <div class="col-md-6 col-md-offset-3 col-lg-4 col-lg-offset-4" style="text-align: center">

                <table class="table table-striped table-bordered">

                    <tbody>

                    <tr>
                        <td style="width: 20px; text-align: center;">
                            Time running
                        </td>
                        <td style="width: 100px; text-align: center;">
                            <%= TimeUtils.timeToString((System.currentTimeMillis() - mcstats.getStartTime()) / 1000) %>
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
                            MySQL queue size
                        </td>
                        <td style="width: 100px; text-align: center;">
                            <%= numberFormatter.format(mcstats.getDatabaseQueue().size()) %>
                        </td>
                    </tr>

                    <tr>
                        <td style="width: 20px; text-align: center;">
                            Executor queue size
                        </td>
                        <td style="width: 100px; text-align: center;">
                            <%= numberFormatter.format(mcstats.getReportHandler().getExecutorQueueSize()) %>
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
                            <%= numberFormatter.format(mcstats.getRequestsAverage().getAverage()) %>
                        </td>
                    </tr>

                    <tr>
                        <td style="width: 20px; text-align: center;">
                            Average API response time
                        </td>
                        <td style="width: 100px; text-align: center;">
                            <%= String.format("%.2f", mcstats.getRequestProcessingTimeAverage().getAverage()) %> ms
                        </td>
                    </tr>

                    <tr>
                        <td style="width: 20px; text-align: center;">
                            Total MySQL queries
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

        <p>Powered by <img src="http://static.mcstats.org/img/digitalocean.png" width="16px"> <a href="http://goo.gl/DdfW0s">DigitalOcean</a> | <a href="http://github.com/Hidendra/mcstats.org"><i class="icon-github"></i> github</a> | <a href="http://blog.mcstats.org"><i class="icon-quote-left"></i> blog</a> | <a href="irc://irc.esper.net/metrics"><i class="icon-comments"></i> irc.esper.net #metrics</a></p>
    </div>
</div>

</body>

</html>