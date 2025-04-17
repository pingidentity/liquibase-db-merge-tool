# Liquibase DB Migration Tool

Liquibase DB Migration Tool migrates data from one database to another. Data can be moved between H2, MySQL and PostgreSQL.

It is a standalone tool that complements software products (such as PingCentral) that use liquibase to move between databases. 

## Introduction

Liquibase DB Migration Tool (dbmerger) is a commandline tool that processes liquibase configurations. After processing those files it is able to read from and write to databases. Using the tool is an oneliner:

- `java -jar dbmerger-1.0.0.jar [options] {absolute-path-to-configuration-file}`

The database connection and other details are provided by a single configuration file:

```json
{
  "change_log_file": "/absolute/path/to/liquibase/changelog-master.xml",
  "source_database": {
    "name": "mysql test database",
    "dbms_type": "mysql",
    "jdbc_url": "jdbc:mariadb://localhost:3306/mydatabase",
    "username": "root",
    "password": "password"
  },
  "target_databases": [
    {
      "name": "postgresql test database",
      "dbms_type": "postgresql",
      "jdbc_url": "jdbc:postgresql://localhost:5432/postgres",
      "username": "postgres",
      "password": "password"
    }
  ]
}
```

## Getting started

**Good to know**

- This setup was developed on a MacBook. If this is used on a Windows machine, the needed instructions may differ
- The document describes processes that work with PingCentral. However, PingCentral is not needed for this tool, only liquibase managed databases

### Template configurations

This repository includes configuration and a json based changelog file examples that work with PingCentral 2.1 and 2.2.

To keep things simple, update the jdbc information in these files before following the *Building the tool* instructions below:

- **./src/main/resources/pingcentral-210-220/config-h2-mysql.json**  // moving data from H2 to MySQL
- **./src/main/resources/pingcentral-210-220/config-h2-postgresql.json**  // moving data from H2 to PostgreSQL
- **./src/main/resources/pingcentral-210-220/config-mysql-postgresql.json**  // moving data from MySQL to PostgreSQL
- **./src/main/resources/pingcentral-210-220/config-postgresql-mysql.json**  // moving data from PostgreSQL to MySQL

By doing so those are included in the tool and can be accessed more easily later. If you prefer, do not update any of those files (except for the H2 ones that need an absolute path to its database files) and use the test databases that come with this repository.

Even if you do not update them now, you can refer to them later. However, in that case also update the value for *change_log_file* to an absolute path.

### Creating a configuration

To create a configuration it is important to understand all details about it. Here we go:

| Key                                                      | Value                                                   | Required | Default | Description                                                                                                                                                                                                                                                                                            |
|----------------------------------------------------------|---------------------------------------------------------|----------|---------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| $.description                                            | My description                                          | -        | -       | Document what this configuration is used for                                                                                                                                                                                                                                                           |
| $.change_log_file                                        | /path/changelog-master.xml                              | X        | -       | Absolute path to the Liquibase changelog-master.xml file. It has to be located as parent for all files it references                                                                                                                                                                                   | 
| $.delete_target_data                                     | true/false                                              | -        | true    | Specify if all data of the target database should be deleted before copying data from the source database. This is recommended to avoid conflicts                                                                                                                                                      |
| $.compare_data                                           | true/false                                              | -        | false   | Compare the data between the source and target database after the data got copied. This may take a while for larger datasets. In certain scenarios (explained later) there will be many desired, differences                                                                                           |
| $.source_database.postgres_type                          | true/false                                              | -        | false   | Specify if the source system is of type PostgreSQL. This is needed as the tool cannot depend on any names as they can be freely chosen. If the source database is of type PostgreSQL and this flag is not set, the transfer of data may be inconsistent. This flag can be ignored for target databases |
| $.source_database.name                                   | db-name                                                 | X        | -       | Specify a name for this database which has to be unique within this file                                                                                                                                                                                                                               |
| $.source_database.dbms_type                              | db-type                                                 | X        | -       | Specify the type of database (mysql, postgresql). It has to mach how this database type is referenced within Liquibase files                                                                                                                                                                           |
| $.source_database.jdbc_url                               | jdbc-url                                                | X        | -       | The jdbc url to connect to the database                                                                                                                                                                                                                                                                |
| $.source_database.username                               | username                                                | X        | -       | The username to connect to the database                                                                                                                                                                                                                                                                |
| $.source_database.password                               | password                                                | X        | -       | The password to connect to the database                                                                                                                                                                                                                                                                |
| $.target_databases.n....                                 | database configuration                                  | X        | -       | Same as for the source database. Even though it is an array, currently exactly one is supported (and required)                                                                                                                                                                                         |
| .n.translate_**to**_postgres_large_clob_object           | postgres largeObject handling for text based data types | X        | -       | A list of tables and their columns for cases where the tool has to handle the PostgreSQL feature of large objects. This is useful (and required for PingCentral) when moving data **to** and **from** PostgreSQL                                                                                       |
| .translate_to_postgres_large_clob_object.n.table         | table name                                              | X        | -       | The name of the table that contains columns that needs to be handled                                                                                                                                                                                                                                   |
| .translate_to_postgres_large_clob_object.n.columns       | list of columns                                         | X/-      | -       | A list of columns that need to be handled. Optional but required if **translate_all=false** (see below) and invalid with **translate_all=true**                                                                                                                                                        |
| .translate_to_postgres_large_clob_object.n.translate_all | true/false                                              | X/-      | false   | If set to true all columns of type **clob** are handled. Optional but required if no columns were listed above (at his time the flag is supported but has no effect)                                                                                                                                   |
| .n.translate_**from**_postgres_large_clob_object         | postgres largeObject handling for text based data types | X        | -       | A list of tables and their columns for cases where the tool has to handle the PostgreSQL feature of large objects. This is useful (and required for PingCentral) when moving data **from** PostgreSQL. The content has the same structure as for translate_**to**_postgres_large_clob_object           |

**Notes:**

- Find many examples of valid and invalid configurations here: **./src/test/resources**

### Building this tool

Required tools:

- Java jdk 11
- Maven
- Make (optional)

Run this command at the root of this project:

- `make build_dbmerger`  // if you do not have Make installed, run the command manually that is found in Makefile

**Note:** This setup uses a MariaDB jdbc driver for MySQL to avoid licensing issues. If the driver dependency in **pom.xml** is updated to a mysql jdbc driver,
 update **src/main/resources/META-INF/MANIFEST.MF** to reference the appropriate jar, update jdbc url's in the config files and run the make command again.

This will produce:

- `./target/dbmerger-1.0.0.jar`

## Options for dbmerger

| Option             | Description                                                                                                                                                                                                                                                                       |
|--------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| validate-config    | validates the configuration file                                                                                                                                                                                                                                                  |
| validate-changelog | validates the Liquibase changelog files                                                                                                                                                                                                                                           |
| validate-source    | connects to the source database                                                                                                                                                                                                                                                   |
| validate-target    | connects to the target database                                                                                                                                                                                                                                                   |
| transfer-data      | transfers data from the source to the target database (the main feature of this tool)                                                                                                                                                                                             |
| changelog-to-json  | converts the Liquibase changelog files into a JSON file. This is useful if the Liquibase changelog files cannot be shared with a third party. This file can be used as an alternative to the changelog-master.xml file, e.g.: point to this file in dbmerger's configuration file |

Logging outputs include any errors that were found.

**Tip:** Always run the *validate-* steps before transferring data.

## Procedure for moving data for PingCentral (as an example)

Before we get to a test setup it is important to understand how the overall process for moving data looks like. Here are the steps:

- Launch PingCentral against H2, MySQL or PostgreSQL (source database)
- Configure PingCentral with templates, applications, connections, certs, users, whatever is needed
- Stop PingCentral
- Configure PingCentral to connect to the target database
- Move **{PINGCENTRAL_HOME}/conf/pingcentral.jwk** to a temporary location but **DO NOT** delete the file
- Launch PingCentral against the target database, wait for the terminal to display **PingCentral running...**
  - This step is required as PingCentral generates the database schema
- Stop PingCentral
  - PingCentral has generated a new **{PINGCENTRAL_HOME}/conf/pingcentral.jwk** which can be deleted as this is not required
- Run **dbmerger**, it will copy all data from MySQL to PostgreSQL
- Copy the original file **pingcentral.jwk** back to **{PINGCENTRAL_HOME}/conf/pingcentral.jwk**
- Launch PingCentral against the target database
- Verify that all configurations are available

```
IMPORTANT: NEVER EVER DELETE THE ORIGINAL FILE {PINGCENTRAL_HOME}/conf/pingcentral.jwk AS PINGCENTRAL BECOMES UNUSABLE!
```

## Using a test setup to move data from MySQL to PostgreSQL

To get a feeling for this tool it can be used with databases that run in docker. It requires the following:

- PingFederate, including a valid license
- PingCentral, including a valid license
- Docker

This repository contains a docker-compose file that launches a MySQL and a PostgreSQL database:

- **./src/test/docker-compose.yml**

Follow these instructions:

- update **/etc/hosts**: `sudo vi /etc/hosts`, add `{your-current-ip-address} dbmerger.mysql.local dbmerger.postgres.local`
- create multiple configurations for PingCentral:
  - copy **{PINGCENTRAL_HOME}/conf/application.properties** to **{PINGCENTRAL_HOME}/conf/application-original.properties** 
  - copy **{PINGCENTRAL_HOME}/conf/application.properties** to **{PINGCENTRAL_HOME}/conf/application-mysql.properties** 
  - copy **{PINGCENTRAL_HOME}/conf/application.properties** to **{PINGCENTRAL_HOME}/conf/application-postgres.properties**

Update **{PINGCENTRAL_HOME}/conf/application-mysql.properties** to include these properties:
```properties
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.url=jdbc:mariadb://dbmerger.mysql.local:3306/dbmerger?createDatabaseIfNotExist=true&useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC&useJvmCharsetConverters=true
spring.datasource.username=root
spring.datasource.password=password
```

Update **{PINGCENTRAL_HOME}/conf/application-postgres.properties** to include these properties:
```properties
spring.datasource.driver-class-name=org.postgresql.Driver
spring.datasource.url=jdbc:postgresql://dbmerger.postgres.local:5432/postgres?createDatabaseIfNotExist=false&useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC&useJvmCharsetConverters=true
spring.datasource.username=postgres
spring.datasource.password=password
```

If PingCentral is connecting to a PingFederate instance that uses a self-signed SSL cert set these properties in both properties files (only for development purposes):

```properties
server.ssl.trust-any=true
server.ssl.https.verify-hostname=false
```

Time to try a full flow:

1. Setup PingCentral against the source database
- open a terminal, cd into **./src/test/database**, run `docker compose up`  // the databases use volumes so that any configuration is available after a restart
- open a terminal, cd into **{PINGCENTRAL_HOME}/conf**, rename **application-mysql.properties** to **application.properties**
- launch PingCentral: `{PINGCENTRAL_HOME}/bin/run.sh`
- open a browser at **https://localhost:9022**, login using **Administrator/ 2Federate**, import the license, set a new password, apply any configurations

2. Stop PingCentral and configure the target database connection
- stop PingCentral
- move **{PINGCENTRAL_HOME}/conf/pingcentral.jwk** to a temporary location
- rename **application.properties** back to **application-mysql.properties** and rename **application-postgres.properties** to **application.properties**

3. Launch PingCentral against the target database
- launch PingCentral, wait for **PingCentral running...**, stop PingCentral, delete the newly generated file **{PINGCENTRAL_HOME}/conf/pingcentral.jwk**

4. Copy the data form source to target
- run dbmerger: cd **./target**, `java -jar dbmerger-1.0.0.jar transfer-data "pingcentral-210-220/config-mysql-postgresql.json"`
- move **pingcentral.jwk** from its temporary location back to **{PINGCENTRAL_HOME}/conf/pingcentral.jwk**

5. Launch PingCentral against the target database
- launch PingCentral, verify that all configurations are available

If everything went well all configurations are available in PingCentral using the target database.

## Using a production setup to move data from MySQL to PostgreSQL

This works the same way as for the test setup. However, here are a few additional notes, tips and thoughts:

- the source database is never modified by the tool
- try moving data from a development or staging system into a test database (for example, the ones of this repository work well for that) to get a sense for this tool
- as mentioned earlier, do **NOT** delete the original **{PINGCENTRAL_HOME}/conf/pingcentral.jwk** file. Any configuration in PingCentral would become unavailable and cannot be recovered
- always transfer data into an empty target database. Otherwise, unwanted conflicts could arise. This is the reason why **dbmerger** deletes data of a target database by default
- when running **dbmerger** with *compare_data=true* many differences will be found if PostgreSQL is involved. This is due to the fact that PostgreSQL stores some large character objects in a referenced, internal large object storage location. The comparison feature will find that MySQL has *real data* whereas PostgreSQL has an **identifier** instead for the same row/ column. Those should be considered as 'expected difference'

## Database commands

Although anyone reading these instructions may be an expert with MySQL or PostgreSQL, here are a few commands to interact with the docker based test databases.

### MySQL

- `docker exec -it dbmerger-mysql bash`
- `mysql -u root -ppassword dbmerger`
- `show tables;`  // list all tables
- `select * from application\G;` // \G creates expanded overview

### PostgresSQL

- `docker exec -it dbmerger-postgres bash`
- `psql -U postgres postgres`
- `\x`  // turn on expanded display
- `\dt` (q to end)  // list all tables

## Links

- Download PingCentral: [https://www.pingidentity.com/en/resources/downloads/pingcentral.html](https://www.pingidentity.com/en/resources/downloads/pingcentral.html)
- Download PingFederate: [https://www.pingidentity.com/en/resources/downloads/pingfederate.html](https://www.pingidentity.com/en/resources/downloads/pingfederate.html)
- Get an easy to configure setup with PingFederate, PingAM and PingDirectory: [https://github.com/pingidentity/webinar-pingfed-pingam](https://github.com/pingidentity/webinar-pingfed-pingam)

# License

This is licensed under the Apache License 2.0.