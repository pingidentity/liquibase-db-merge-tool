# Liquibase DB Migration Tool for PingCentral

This branch includes pre-configured configurations and tips for using dbmerger with PingCentral.

For general information, how to build and use this tool, please see the **main** branch.

### Template configurations

This repository includes configuration and a json based changelog file that work with PingCentral 2.1 and 2.2.

The below files are all readily configured but they need jdbc-driver and path updates:

- **./pingcentral-210-220/config-h2-mysql.json**  // moving data from H2 to MySQL
- **./pingcentral-210-220/config-h2-postgresql.json**  // moving data from H2 to PostgreSQL
- **./pingcentral-210-220/config-mysql-postgresql.json**  // moving data from MySQL to PostgreSQL
- **./pingcentral-210-220/config-postgresql-mysql.json**  // moving data from PostgreSQL to MySQL

## Procedure for moving data for PingCentral

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
- Run **dbmerger**, it will copy all data from source database to target database
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

4. Test the configuration and copy the data form source to target
- run dbmerger: cd **./target**, `java -jar dbmerger-1.0.0.jar validate-config "/absolute/path/to/pingcentral-210-220/config-mysql-postgresql.json"`
- run dbmerger: cd **./target**, `java -jar dbmerger-1.0.0.jar validate-changelog "/absolute/path/to/pingcentral-210-220/config-mysql-postgresql.json"`
- run dbmerger: cd **./target**, `java -jar dbmerger-1.0.0.jar validate-source "/absolute/path/to/pingcentral-210-220/config-mysql-postgresql.json"`
- run dbmerger: cd **./target**, `java -jar dbmerger-1.0.0.jar validate-target "/absolute/path/to/pingcentral-210-220/config-mysql-postgresql.json"`
- run dbmerger: cd **./target**, `java -jar dbmerger-1.0.0.jar transfer-data "/absolute/path/to/pingcentral-210-220/config-mysql-postgresql.json"`
- move **pingcentral.jwk** from its temporary location back to **{PINGCENTRAL_HOME}/conf/pingcentral.jwk**

5. Launch PingCentral against the target database
- launch PingCentral, verify that all configurations are available

If everything went well all configurations are available in PingCentral using the target database.

## Using a production setup to move data from MySQL to PostgreSQL

This works the same way as for the test setup. However, here are a few additional notes, tips and thoughts:

- as mentioned earlier, do **NOT** delete the original **{PINGCENTRAL_HOME}/conf/pingcentral.jwk** file. Any configuration in PingCentral would become unavailable and cannot be recovered

## Links

- Download PingCentral: [https://www.pingidentity.com/en/resources/downloads/pingcentral.html](https://www.pingidentity.com/en/resources/downloads/pingcentral.html)
- Download PingFederate: [https://www.pingidentity.com/en/resources/downloads/pingfederate.html](https://www.pingidentity.com/en/resources/downloads/pingfederate.html)
- Get an easy to configure setup with PingFederate, PingAM and PingDirectory: [https://github.com/pingidentity/webinar-pingfed-pingam](https://github.com/pingidentity/webinar-pingfed-pingam)

# License

This is licensed under the Apache License 2.0.