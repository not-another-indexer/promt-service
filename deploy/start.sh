#!/bin/sh

rm -rf cloudberry-storage
rm -rf prompt-service
rm -rf nai-frontend

if [ ! -d "./cloudberry-storage/.git" ]; then
  git clone https://github.com/not-another-indexer/cloudberry-storage.git cloudberry-storage
fi

if [ ! -d "./nai-frontend/.git" ]; then
  git clone https://github.com/not-another-indexer/nai-frontend.git nai-frontend
  cd nai-frontend
  git clone https://github.com/not-another-indexer/protos.git
  cd ../
fi

if [ ! -d "./prompt-service/.git" ]; then
  git clone https://github.com/not-another-indexer/promt-service.git prompt-service
fi

sudo docker-compose up --build
