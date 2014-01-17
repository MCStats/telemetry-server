#!/bin/bash

# get the start unix timestamp
START=`date +%s`

echo -e " [\e[1;33m++\e[00m] Compiling"
mvn --quiet clean package

chmod -R 777 .

echo -e " [\e[1;33m++\e[00m] Uploading build"
rsync -av --progress --exclude=*.tar.gz --exclude=archive-tmp --exclude=classes --exclude=maven-archiver --exclude=surefire target/ root@host.ny.mcstats.org:/data/jails/backend01.ny.mcstats.org/home/mcstats/
rsync -av --progress server-definitions.txt root@host.ny.mcstats.org:/data/jails/backend01.ny.mcstats.org/home/mcstats/

echo -e " [\e[1;33m++\e[00m] Fixing permissions"
ssh root@host.ny.mcstats.org "chown -R 1001:1001 /data/jails/backend01.ny.mcstats.org/home/mcstats/"

# finish timestamp, calculate runtime
FINISH=`date +%s`
RUNTIME=`echo $FINISH - $START | bc`

echo -e " [\e[0;32m!!\e[00m] Finished deploy ($RUNTIME seconds)"
