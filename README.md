# arbeitszeit

This is a small [ammonite](https://ammonite.io/) script which can be used to fill in arbeitszeit sheets from TU Darmstadt.

## Usage
To run this script you need to have [ammonite](https://ammonite.io/) as well as [pdftk](https://www.pdflabs.com/tools/pdftk-the-pdf-toolkit/) installed.

`amm arbeitszeit.sc --form arbeitszeit2019.pdf --name "Kim Mustermann" --birthday "01.01.1989" --institution "Evil Corp." --month 1 --year 2000 --hours 60`


<!-- ---
## TODO
- [x] Ensure that generated entries don't overlap && that max time per day is not exceeded
- [ ] Webservice to generate Stundenzettel as a service
- [x] Read command line arguments
- [x] pretty output -->
