# Используем официальный образ Tomcat с JDK
FROM tomcat:9.0-jdk17

# Удаляем стандартные приложения Tomcat
RUN rm -rf /usr/local/tomcat/webapps/*

# Копируем WAR файл приложения
COPY target/SQLTutor.war /usr/local/tomcat/webapps/ROOT.war

# Открываем порт
EXPOSE 8080

# Запускаем Tomcat
CMD ["catalina.sh", "run"]