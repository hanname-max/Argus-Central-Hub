FROM eclipse-temurin:21-jre-alpine AS builder

WORKDIR /application

ARG JAR_FILE=target/argus-central-hub-1.0.0-SNAPSHOT.jar

COPY ${JAR_FILE} application.jar

RUN java -Djarmode=layertools -jar application.jar extract

FROM eclipse-temurin:21-jre-alpine

WORKDIR /application

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /application/dependencies/ ./
COPY --from=builder /application/spring-boot-loader/ ./
COPY --from=builder /application/snapshot-dependencies/ ./
COPY --from=builder /application/application/ ./

RUN chown -R appuser:appgroup /application

USER appuser

ENV JAVA_OPTS=" \
    -XX:+UseZGC \
    -XX:+ZGenerational \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+ExitOnOutOfMemoryError \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/tmp \
    -XX:+ParallelRefProcEnabled \
    -XX:ParallelGCThreads=2 \
    -XX:ConcGCThreads=1 \
    -XX:ZCollectionIntervalMinutes=15 \
    -XX:+ExplicitGCInvokesConcurrent \
    -XX:+UseStringDeduplication \
    -XX:+OptimizeStringConcat \
    -XX:+EliminateAllocations \
    -XX:+UseCompressedOops \
    -XX:+UseCompressedClassPointers \
    -XX:+TieredCompilation \
    -XX:+AlwaysPreTouch \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.profiles.active=prod \
    -Djava.util.concurrent.ForkJoinPool.common.parallelism=2 \
    -Dspring.threads.virtual.enabled=true \
"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
