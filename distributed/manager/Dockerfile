FROM ubuntu:20.04
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y curl cmake python3 python3-pip openjdk-11-jdk libprotobuf-java git protobuf-compiler && git clone -b distributed_vdms https://github.com/cwlacewe/vdms.git && pip3 install vdms

RUN cd / && curl https://storage.googleapis.com/google-code-archive-downloads/v2/code.google.com/json-simple/json-simple-1.1.1.jar -o json-simple-1.1.1.jar && mv json-simple-1.1.1.jar /usr/share/java && curl https://mirrors.gigenet.com/apache//commons/codec/binaries/commons-codec-1.15-bin.tar.gz -o commons-codec-1.15-bin.tar.gz && gunzip commons-codec-1.15-bin.tar.gz && tar -xf commons-codec-1.15-bin.tar && mv commons-codec-1.15/commons-codec-1.15.jar /usr/share/java

RUN cd /vdms/distributed/manager &&  mkdir build && cd build && cp ../../../utils/src/protobuf/queryMessage.proto ./ && cmake .. && make -j16

WORKDIR /vdms/distributed/manager/build
ENTRYPOINT ["java", "-cp", "TestServer.jar", "TestServer"]
#ENTRYPOINT ["tail", "-f"]