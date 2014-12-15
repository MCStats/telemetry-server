#!/bin/bash

# get the start unix timestamp
START=`date +%s`

echo -e " [\x1B[1;33m++\x1B[00m] Compiling"
mvn --quiet clean package

chmod -R 777 .

echo -e " [\x1B[1;33m++\x1B[00m] Uploading build"
rsync -av --progress --exclude=*.tar.gz --exclude=archive-tmp --exclude=classes --exclude=maven-archiver --exclude=surefire target/ root@107.170.0.143:/home/mcstats/
rsync -av --progress server-definitions.txt root@107.170.0.143:/home/mcstats/

echo -e " [\x1B[1;33m++\x1B[00m] Fixing permissions"
ssh root@107.170.0.143 "chown -R 100:100 /home/mcstats/"
ssh root@107.170.0.143 "chmod -R 777 /home/mcstats/"

# finish timestamp, calculate runtime
FINISH=`date +%s`
RUNTIME=`echo $FINISH - $START | bc`

echo -e " [\x1B[0;32m!!\x1B[00m] Finished deploy ($RUNTIME seconds)"
