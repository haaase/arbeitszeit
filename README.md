# arbeitszeit

~~This is a small [ammonite](https://ammonite.io/) script which can be used to fill in arbeitszeit sheets from TU Darmstadt.~~

Arbeitszeit is now a CLI-Application as well as a Webservice! The old Ammonite files can be found in the [scripts/](scripts/) folder.

## CLI Usage

To run the CLI Application, grab the latest cli jar from the [releases](https://github.com/haaase/arbeitszeit/releases) page. This is a fat jar that you can run by doing:

```
java -jar arbeitszeit-cli-X.X.X.jar --form arbeitszeit2019.pdf --name "Kim Mustermann" --birthday "01.01.1989" --institution "Evil Corp." --month 1 --year 2000 --hours 60
```

## Webservice Usage

To run the webservice, get the latest web jar from the [releases](https://github.com/haaase/arbeitszeit/releases) page. You can start the server by placing the form as `form.pdf` in the same directory and running:

```
java -jar arbeitszeit-web.X.X.X.jar
```

This starts a server serving the web API. You can reach it via `localhost:8080/pdf/`.
An example call looks like: [http://localhost:8080/pdf/bla.pdf?name=Kim%20Mustermann&month=2&year=2012&birthday=01.01.1989](http://localhost:8080/pdf/output.pdf?name=Kim%20Mustermann&month=2&year=2012&birthday=01.01.1989)
It accepts all of the scripts' options as GET parameters and serves a downloadable PDF file.

## Try it!
You can try the webservice at online at [https://arbeitszeit.herokuapp.com/pdf/output.pdf?name=Kim%20Mustermann&month=2&year=2012&birthday=01.01.1989](https://arbeitszeit.herokuapp.com/pdf/output.pdf?name=Kim%20Mustermann&month=2&year=2012&birthday=01.01.1989)!

