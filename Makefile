REGISTRY = docker.io/rojassluu
TAG ?= latest

build:
	docker build -t $(REGISTRY)/gateway-service:$(TAG) ./gateway-service
	docker build -t $(REGISTRY)/pedidos-service:$(TAG) ./pedidos-service
	docker build -t $(REGISTRY)/usuarios-service:$(TAG) ./usuarios-service

push:
	docker push $(REGISTRY)/gateway-service:$(TAG)
	docker push $(REGISTRY)/pedidos-service:$(TAG)
	docker push $(REGISTRY)/usuarios-service:$(TAG)
