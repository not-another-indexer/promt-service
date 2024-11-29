#!/bin/sh

#rm -rf cloudberry-storage
#rm -rf prompt-service

if [ ! -d "./cloudberry-storage/.git" ]; then
  git clone https://github.com/not-another-indexer/cloudberry-storage.git cloudberry-storage
fi

if [ ! -d "./prompt-service/.git" ]; then
  git clone https://github.com/not-another-indexer/promt-service.git prompt-service
fi

docker-compose up --build
