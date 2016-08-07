# TEMPLATE-SITE 0.1.2
Originally forked from Tetrao (https://github.com/tetrao-eu/template-site)

**Template Site** is an skeleton of a web application based on:

* [Scala 2.11.8 as programming language](http://www.scala-lang.org/)
* [SBT 0.13.11 as build tool](http://www.scala-sbt.org/)
* [Play Framework 2.5.4 as web application framework](https://www.playframework.com/)
* [Play2-auth 0.14.2 as authentication module for Play](https://github.com/t2v/play2-auth/)
* [PostgreSQL 9.5 as database](http://www.postgresql.org)
* [Slick 3.1.1 as database access layer](http://slick.lightbend.com)
* [Slick-pg 0.14.1 extending slick for support PostgreSQL data types](https://github.com/tminglei/slick-pg)
* [Foundation 6.2.3 as front-end framework](http://foundation.zurb.com)

The application is a website where you have to login to enter in a restricted page. The restricted page is a messages
page, where you can see, add or delete messages that are shared beetween all the users. Also is possible to edit your
own user and, if you are and admin user, you can add other non admin users.

Default users are `admin@email.com`, the admin user, and `bob@email.com` a *normal* user. Both users have the same
password and it is `password`.

## Getting the project
As usual:

    git clone https://github.com/asciiu/template-site.git

The following commands are written for a GNU/Linux environment (**Debian**, if you want to know). So please adapt it
to your system if it is necessary. Also they are running supposing that you are locating inside the folder
`template-site`, the root of this project.

## Database

### Database setup
Assuming you have already installed postgres create a user and the database. Substitute your own names where
appropriate.

    sudo -u postgres psql -c "CREATE USER player PASSWORD 'password';"
    sudo -u postgres psql -c "CREATE DATABASE play_example_db;"
    sudo -u postgres psql -c "ALTER DATABASE play_example_db OWNER TO player;"

**IMPORTANT!** If you change any database config value, please remember to update the config file
`conf/application.conf`

## Mailer Setup
Add your mail server settings to application.conf under play.mailer. Set 'mock' to false to send emails.


### Database mapping code
The file `models.db.Tables.scala` contains the database mapping code. It has been generated running the main class
`utils.db.SourceCodeGenerator`. If you want to regenerate the database mapping code for any reason, check the
config file `conf/application.conf` and run:

    sbt tables

## SBT

To run the project execute:

    sbt run

And open a browser with the url [http://localhost:9000](http://localhost:9000)

The plugin [sbt-updates](https://github.com/rtimush/sbt-updates) is installed (see `plugins.sbt`). To check
if all the dependencies are up to date, it is necessary to execute:

    sbt dependencyUpdates

## License
Licensing conditions (MIT) can be found in `LICENSE` file.
