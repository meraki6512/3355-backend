# --- 실행(Runtime) 단계 ---
# Java 21 JRE(Java 실행 환경)만 포함된 가벼운 이미지 사용
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# CI 러너의 파일 시스템에서 빌드된 .jar 파일을 '최종 이미지'로 바로 복사
COPY api-server/build/libs/api-server-*.jar app.jar

# api-server는 8080 포트 사용
EXPOSE 8080

# 컨테이너 시작 시 실행할 명령어
ENTRYPOINT ["java", "-Xms2g", "-Xmx2g", "-Xlog:gc*:stdout:time,level,tags", "-jar", "app.jar"]