FROM mcstats/oracle-java8-jdk
MAINTAINER Tyler Blair <hidendra@mcstats.org>

RUN \
  apt-get update && \
  apt-get -y upgrade && \
  apt-get -y --no-install-recommends install maven && \
  apt-get clean && \
  rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

# Pull in source & build it
WORKDIR /tmp/build
ADD . .
RUN mvn clean package
RUN mkdir /home/app && cp -v target/*.jar /home/app

# cleanup the build env
WORKDIR /home/app
RUN rm -rf /tmp/build ~/.m2

# Run script
ADD docker/run.sh /
RUN chmod 755 /run.sh

CMD ["/bin/bash", "/run.sh"]
