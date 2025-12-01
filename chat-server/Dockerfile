# [수정] 'builder' 스테이지(FROM gradle...) 전부 삭제

# --- 실행(Runtime) 단계 ---
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# CI 러너에서 chat-server의 .jar 파일을 '최종 이미지'로 복사
COPY chat-server/build/libs/chat-server-*.jar app.jar

# chat-server는 8081 포트 사용
EXPOSE 8081

# 컨테이너 시작 시 실행할 명령어
# 챗 서버는 동시 연결 수가 많을 수록 메모리 사용량이 증가하기 때문에, 우선 4기가로 설정해 부하 대비함
ENTRYPOINT ["java", "-Xms4g", "-Xmx4g", "-Xlog:gc*:stdout:time,level,tags", "-jar", "app.jar"]
