# Poloniex Bot 0.0.0a

Default users are `admin@email.com`, the admin user, and `bob@email.com` a *normal* user. Both users have the same
password and it is `password`.

### Database setup
Assuming you have already installed postgres create a user and the database. Substitute your own names where
appropriate.

    sudo -u postgres psql -c "CREATE USER player PASSWORD 'password';"
    sudo -u postgres psql -c "CREATE DATABASE play_example_db;"
    sudo -u postgres psql -c "ALTER DATABASE play_example_db OWNER TO player;"

**IMPORTANT!** If you change any database config value, please remember to update the config file
`conf/application.conf`


### Database mapping code
The file `models.db.Tables.scala` contains the database mapping code. It has been generated running the main class
`utils.db.SourceCodeGenerator`. If you want to regenerate the database mapping code for any reason, check the
config file `conf/application.conf` and run:

    sbt gen-tables

### Database config (required!)
The app assumes the timezone set for the postgres server is UTC. You can set the default timezone per session for
the server's DB or set it globally via the server's postgresql.conf file.

Add this:
"timezone = 'UTC'"


## Mailer Setup
Add your mail server settings to application.conf under play.mailer. Set 'mock' to false to send emails.

## SBT

To run the project execute:

    sbt run

And open a browser with the url [http://localhost:9000](http://localhost:9000)

The plugin [sbt-updates](https://github.com/rtimush/sbt-updates) is installed (see `plugins.sbt`). To check
if all the dependencies are up to date, it is necessary to execute:

    sbt dependencyUpdates
