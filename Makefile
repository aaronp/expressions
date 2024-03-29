include ./Makefile.vars

buildDocker:
	./build.sh

run: buildDocker
	mkdir ./docker-data && docker run -p 8080:8080 -v `pwd`/docker-data:/app/data porpoiseltd/expressions:latest

buildFlutterUI:
	cd ui && flutter build web

startLocalKafka:
	# cd ./franz/src/test/resources/docker/; docker-compose up --remove-orphans &

	docker run --rm -p 2181:2181 -p 3030:3030 -p 8081-8083:8081-8083 \
           -p 9581-9585:9581-9585 -p 9092:9092 -e ADV_HOST=`ipconfig getifaddr en0` \
           lensesio/fast-data-dev:latest

listTopics:
	docker run --rm --network="host" landoop/fast-data-dev:2.2.0 kafka-topics --zookeeper 127.0.0.1:2181 --list
deleteTopic:
	docker run --rm --network="host" landoop/fast-data-dev:2.2.0 kafka-topics --zookeeper 127.0.0.1:2181 --delete --topic $(topic)
readTopic:
	docker run --rm --network="host" landoop/fast-data-dev:2.2.0 kafka-console-consumer --topic $(topic) --from-beginning --bootstrap-server 127.0.0.1:9092
readPaged:
	docker run --rm --network="host" landoop/fast-data-dev:2.2.0 kafka-console-consumer --key-deserializer --value-deserializer --property print.key=true --property print.value=true  --topic $(topic) --offset $(o) --partition $(p) --bootstrap-server 127.0.0.1:9092
kafka:
	docker run --rm -it --network="host" landoop/fast-data-dev:2.2.0 /bin/bash
lenses-io:
	docker run -e ADV_HOST=127.0.0.1 -e EULA="http://dl.lenses.io/d/?id=6fc327c1-5435-4dd7-be75-e767f89ad6a1" --rm -p 3030:3030 -p 9092:9092 lensesio/box