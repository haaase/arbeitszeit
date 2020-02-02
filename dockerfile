FROM openjdk:14-slim-buster

COPY web/target/scala-2.12/arbeitszeit-web-assembly-0.3.0.jar /usr/src/myapp/app.jar
COPY form.pdf /usr/src/myapp/form.pdf
WORKDIR /usr/src/myapp

# fix installation issues
RUN mkdir /usr/share/man/man1
# install pdftk
RUN apt-get update
RUN apt-get -y install pdftk

CMD ["java", "-jar", "app.jar"]