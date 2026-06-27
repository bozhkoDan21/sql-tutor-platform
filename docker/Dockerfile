# Используем официальный образ Tomcat с JDK
FROM tomcat:9.0-jdk17

# Удаляем стандартные приложения Tomcat
RUN rm -rf /usr/local/tomcat/webapps/*

# Копируем WAR файл приложения
COPY target/SQLTrainer.war /usr/local/tomcat/webapps/ROOT.war

# Устанавливаем UTF-8 везде
ENV LANG ru_RU.UTF-8
ENV LC_ALL ru_RU.UTF-8
ENV JAVA_OPTS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"

# Настраиваем Tomcat на UTF-8
RUN sed -i 's/<Connector port="8080"/<Connector port="8080" URIEncoding="UTF-8" useBodyEncodingForURI="true"/' /usr/local/tomcat/conf/server.xml

# Копируем сертификат для HTTPS
COPY keystore.jks /usr/local/tomcat/conf/keystore.jks

# Настраиваем HTTPS connector на порту 8443
RUN sed -i '/<Service name="Catalina">/a \    <Connector port="8443" protocol="org.apache.coyote.http11.Http11NioProtocol" maxThreads="150" SSLEnabled="true" scheme="https" secure="true" clientAuth="false" sslProtocol="TLS" keystoreFile="/usr/local/tomcat/conf/keystore.jks" keystorePass="teacher123" />' /usr/local/tomcat/conf/server.xml

# Открываем порты HTTP и HTTPS
EXPOSE 8080
EXPOSE 8443

# Запускаем Tomcat
CMD ["catalina.sh", "run"]