# ==========================================================================
#  Multi-stage build cho notification-service (multi-module Gradle).
#  Build context la THU MUC GOC cua repo:
#     docker build -f notification-service/Dockerfile -t omnichannel/notification-service .
#
#  LUU Y: phai COPY TOAN BO repo vao image, khong chi rieng module nay.
#  settings.gradle include ca 7 module, ma Gradle 9 bat buoc moi project da
#  include phai co thu muc that -> thieu bat ky module nao se loi
#  "Configuring project ... without an existing directory is not allowed".
# ==========================================================================

# ---------- Stage 1: build ----------
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /workspace

# Copy truoc phan khung Gradle va tai san Gradle distribution -> layer nay
# duoc cache, khong phai tai lai ~130MB moi lan sua code.
COPY gradlew ./
COPY gradle ./gradle
RUN chmod +x ./gradlew && ./gradlew --version --no-daemon

# Copy toan bo source (xem .dockerignore de biet phan bi loai tru)
COPY . .

RUN ./gradlew :notification-service:bootJar --no-daemon

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=builder /workspace/notification-service/build/libs/*.jar app.jar

EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
