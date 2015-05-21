#!/bin/bash

cd /home/app/

exec java -server \
        -XX:+UseG1GC \
        -Xmx512M \
        -Dfile.encoding=UTF-8 \
        -Djava.net.preferIPv4Stack=true \
        -cp $(echo *.jar | tr ' ' ':') \
        -XX:-OmitStackTraceInFastThrow \
        org.mcstats.Main
