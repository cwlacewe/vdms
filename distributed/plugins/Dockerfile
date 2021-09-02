# Pull base image.
FROM ubuntu:20.04 as plugin_base
#needed so jdk installs without prompting user for response
ENV DEBIAN_FRONTEND=noninteractive

#Install Packages
RUN apt-get update && apt-get -y install curl maven g++ cmake python3 python3-pip openjdk-11-jdk-headless libprotobuf-java git protobuf-compiler unzip && cd / && git clone -b distributed_vdms https://github.com/cwlacewe/vdms.git && git clone https://github.com/google/protobuf.git && cd /protobuf/cmake/ && mkdir build && cd build &&  cmake -Dprotobuf_BUILD_TESTS=OFF .. && make -j8 && cp protoc ../../src/protoc && cd /protobuf/java/core && mvn package -Dhttps.nonProxyHosts="localhost|127.0.0.1" && cp $(ls target/protobuf-java*.jar) /usr/share/java/protobuf.jar

RUN cd /vdms && cd distributed/plugins &&  mkdir build && cd build && cp ../../../utils/src/protobuf/queryMessage.proto ./ && cmake .. && make -j16

ENTRYPOINT ["java", "-cp", "TestPlugin.jar:/usr/share/java/protobuf.jar", "TestPlugin"]
#ENTRYPOINT ["tail", "-f"]