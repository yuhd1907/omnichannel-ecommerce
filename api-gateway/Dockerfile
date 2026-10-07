# ==========================================================================
#  Multi-stage build cho api-gateway (multi-module Gradle).
#  Build context la THU MUC GOC cua repo:
#     docker build -f api-gateway/Dockerfile -t omnichannel/api-gateway .
#
#  LUU Y: phai COPY TOAN BO repo vao image, khong chi rieng module nay.
#  settings.gradle include ca 7 module, ma Gradle 9 bat buoc moi project da
#  include phai co thu muc that -> thieu bat ky module nao se loi
#  "Configuring project ... without an existing directory is not allowed".
# ==========================================================================

# ---------- Stage 1: build ----------
# KHONG dung ban -alpine o stage build: protoc-gen-grpc-java (grpc-api) la
# binary link glibc, Alpine dung musl -> "program not found or is not executable".
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /workspace

# Copy truoc phan khung Gradle va tai san Gradle distribution -> layer nay
# duoc cache, khong phai tai lai ~130MB moi lan sua code.
COPY gradlew ./
COPY gradle ./gradle
RUN chmod +x ./gradlew && ./gradlew --version --no-daemon

# Copy toan bo source (xem .dockerignore de biet phan bi loai tru)
COPY . .

RUN ./gradlew :api-gateway:bootJar --no-daemon

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=builder /workspace/api-gateway/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
