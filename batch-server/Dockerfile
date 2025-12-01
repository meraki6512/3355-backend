# --- 실행(Runtime) 단계 ---
# Java 21 JRE(Java 실행 환경)만 포함된 가벼운 이미지 사용
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# CI 러너의 파일 시스템에서 빌드된 .jar 파일을
# '최종 이미지'로 바로 복사
COPY batch-server/build/libs/batch-server-*.jar app.jar

# (Batch 서버는 외부 포트를 열 필요가 없음)

# 컨테이너 시작 시 실행할 명령어
# 배치 서버는 대용량 데이터 처리 시 OOM 위험이 있어서 4기가로 설정함
ENTRYPOINT ["java", "-Xms4g", "-Xmx4g", "-Xlog:gc*:stdout:time,level,tags", "-jar", "app.jar"]
