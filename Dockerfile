FROM ubuntu:22.04

RUN \
    apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y \
        ca-certificates-java \
        curl \
        graphviz \
        openjdk-8-jre-headless \
        python3-distutils \
        && \
    rm -rf /var/lib/apt/lists/*

RUN curl https://bootstrap.pypa.io/get-pip.py -o get-pip.py
RUN python3 get-pip.py
RUN pip3 install notebook

RUN pip3 install jupyterlab==3.6.6
RUN pip3 install RISE
RUN pip3 install jupyter_contrib_nbextensions
RUN jupyter nbextension enable splitcell/splitcell

ENV SCALA_VERSION=2.13.14
ENV ALMOND_VERSION=0.14.0-RC15

ENV COURSIER_CACHE=/coursier_cache

ADD . /chisel-bootcamp/
WORKDIR /chisel-bootcamp

ENV JUPYTER_CONFIG_DIR=/jupyter/config
ENV JUPITER_DATA_DIR=/jupyter/data

RUN mkdir -p $JUPYTER_CONFIG_DIR/custom

RUN mkdir /coursier_cache

RUN \
    curl -L -o coursier https://git.io/coursier-cli && \
    chmod +x coursier && \
    ./coursier \
        bootstrap \
        -r jitpack \
        sh.almond:scala-kernel_$SCALA_VERSION:$ALMOND_VERSION \
        --sources \
        --default=true \
        -o almond && \
    ./almond --install --global && \
    \rm -rf almond couriser /root/.cache/coursier 

WORKDIR /

EXPOSE 8888
# CMD jupyter notebook --no-browser --ip 0.0.0.0 --port 8888
# CMD /bin/bash