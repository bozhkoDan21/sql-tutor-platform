# Используем официальный образ Tomcat с JDK
FROM tomcat:9.0-jdk17

# Удаляем стандартные приложения Tomcat
RUN rm -rf /usr/local/tomcat/webapps/*

# Копируем WAR файл приложения
COPY target/SQLTutor.war /usr/local/tomcat/webapps/ROOT.war

# Устанавливаем UTF-8 везде
ENV LANG ru_RU.UTF-8
ENV LC_ALL ru_RU.UTF-8
ENV JAVA_OPTS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"

# Настраиваем Tomcat на UTF-8
RUN sed -i 's/<Connector port="8080"/<Connector port="8080" URIEncoding="UTF-8" useBodyEncodingForURI="true"/' /usr/local/tomcat/conf/server.xml

# Открываем порт
EXPOSE 8080

# Запускаем Tomcat
CMD ["catalina.sh", "run"]