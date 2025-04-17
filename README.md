# Liquibase DB Migration Tool

Liquibase DB Migration Tool migrates data from one database to another. Data can be moved between H2, MySQL and PostgreSQL.

It is a standalone tool that complements software products that use liquibase to manage databases. 

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
| .n.translate_**to**_postgres_large_clob_object           | postgres largeObject handling for text based data types | X        | -       | A list of tables and their columns for cases where the tool has to handle the PostgreSQL feature of large objects. This is useful when moving data **to** and **from** PostgreSQL                                                                                       |
| .translate_to_postgres_large_clob_object.n.table         | table name                                              | X        | -       | The name of the table that contains columns that needs to be handled                                                                                                                                                                                                                                   |
| .translate_to_postgres_large_clob_object.n.columns       | list of columns                                         | X/-      | -       | A list of columns that need to be handled. Optional but required if **translate_all=false** (see below) and invalid with **translate_all=true**                                                                                                                                                        |
| .translate_to_postgres_large_clob_object.n.translate_all | true/false                                              | X/-      | false   | If set to true all columns of type **clob** are handled. Optional but required if no columns were listed above (at his time the flag is supported but has no effect)                                                                                                                                   |
| .n.translate_**from**_postgres_large_clob_object         | postgres largeObject handling for text based data types | X        | -       | A list of tables and their columns for cases where the tool has to handle the PostgreSQL feature of large objects. This is useful when moving data **from** PostgreSQL. The content has the same structure as for translate_**to**_postgres_large_clob_object           |

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

## Using a test setup to move data from MySQL to PostgreSQL

To get a feeling for this tool it can be used with databases that run in docker. It requires the following:

- Docker

This repository contains a docker-compose file that launches a MySQL and a PostgreSQL database:

- **./src/test/docker-compose.yml**  // review the file

Follow these instructions:

- update **/etc/hosts**: `sudo vi /etc/hosts`, add `{your-current-ip-address} dbmerger.mysql.local dbmerger.postgres.local`
- open a terminal, cd into **./src/test/database**, run `docker compose up`  // the databases use volumes so that any configuration is available after a restart
- run your existing liquibase managed database scripts to create a database schema in one or both test databases
- run dbmerger to copy data from your original database into one of these test databases

## Tips and additional information

- the source database is never modified by the tool
- try moving data from a development or staging system into a test database (for example, the ones of this repository work well for that) to get a sense for this tool
- always transfer data into an empty target database. Otherwise, unwanted conflicts could arise. This is the reason why **dbmerger** deletes data of a target database by default
- when running **dbmerger** with *compare_data=true* many differences may be found if PostgreSQL is involved. This is due to the fact that PostgreSQL stores some large character objects in a referenced, internal large object storage location. The comparison feature will find that MySQL has *real data* whereas PostgreSQL has an **identifier** instead for the same row/ column. Those should be considered as 'expected differences'

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

- PingIdentity: [https://pingidentity.com](https://pingidentity.com)
- PingIdentity in GitHub: [https://github.com/pingidentity](https://github.com/pingidentity)

# PingCentral

If you are a PingCentral customer checkout the branch **feature/pingcentral** which contains prepared and ready to use configurations.

# License

This is licensed under the Apache License 2.0.