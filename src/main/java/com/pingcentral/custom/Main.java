package com.pingcentral.custom;

import java.io.File;
import java.io.FileWriter;
import java.util.Date;
import java.util.logging.Logger;

public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    private static final String validateConfig = "validate-config";
    private static final String validateChangelog = "validate-changelog";
    private static final String validateSource = "validate-source";
    private static final String validateTarget = "validate-target";
    private static final String changelogToJson = "changelog-to-json";
    private static final String transferData = "transfer-data";

    public static void main(String[] args) {
        if (args.length != 2) {
            LOGGER.info(help());
            System.exit(0);
        }
        try {
            String feature = args[0];
            String configFileLocation = args[1];
            switch (feature) {
                case validateConfig: {
                    DatabaseManager dm = new DatabaseManager(configFileLocation);
                    LOGGER.info(String.format("The configuration is valid. It will transfer data from %s (type=%s) to %s (type=%s) and is set to delete target data: %b",
                            dm.getConfig().getSourceDatabase().getName(),
                            dm.getConfig().getSourceDatabase().getDbmsType(),
                            dm.getConfig().getTargetDatabases().get(0).getName(),
                            dm.getConfig().getTargetDatabases().get(0).getDbmsType(),
                            dm.getConfig().isDeleteTargetData()));
                    break;
                }
                case validateChangelog: {
                    DatabaseManager dm = new DatabaseManager(configFileLocation);
                    dm.processLiquibaseFiles();
                    LOGGER.info("The changelog files can be processed");
                    break;
                }
                case validateSource: {
                    DatabaseManager dm = new DatabaseManager(configFileLocation);
                    dm.initDb(true, false);
                    dm.closeDb(true, false);
                    LOGGER.info("The source database is accessible");
                    break;
                }
                case validateTarget: {
                    DatabaseManager dm = new DatabaseManager(configFileLocation);
                    dm.initDb(false, true);
                    dm.closeDb(false, true);
                    LOGGER.info("The target database is accessible");
                    break;
                }
                case changelogToJson: {
                    DatabaseManager dm = new DatabaseManager(configFileLocation);
                    dm.processLiquibaseFiles();
                    File f = new File(String.format("changelog-%s.json", new Date().getTime()));
                    FileWriter fw = new FileWriter(f);
                    fw.write( dm.getChangelogAsJson() );
                    fw.close();
                    LOGGER.info(String.format("The changelog-json file was generated at: %s", f.getAbsolutePath()));
                    break;
                }
                case transferData: {
                    DatabaseManager dm = new DatabaseManager(configFileLocation);
                    // read the changelog-master file and produce a list of all liquibase config files
                    // process the Liquibase config files
                    dm.processLiquibaseFiles();
                    // initialize the database connections
                    dm.initDb(true, true);
                    // optionally delete all data from the target database
                    dm.deleteDataFromTargetDbms();
                    // copy all data to the target database
                    dm.transferFromDbToDb();
                    // optionally compare target and source database
                    dm.compareFromDbToDb();
                    // close the database connections
                    dm.closeDb(true, true);
                    break;
                }
                default: {
                    LOGGER.info(help());
                }
            }
        } catch (Exception e) {
            LOGGER.warning(e.getMessage());
            System.exit(0);
        }
    }

    private static String help() {
        StringBuilder sb = new StringBuilder();
        sb.append("Usage:\n");
        sb.append("- java -jar dbmerger-{version}.jar [option] {absolute-path-to-config-file}\n");
        sb.append("Options:\n\t");
        sb.append("validate-config: validate the given configuration file\n\t");
        sb.append("validate-changelog: validate the referenced changelog files\n\t");
        sb.append("validate-source: test the connection to the source database\n\t");
        sb.append("validate-target: test the connection to the target database\n\t");
        sb.append("transfer-data: transfer data from the source to the target database\n\t");
        sb.append("changelog-to-json: processes the liquibase changelog files into a json file (changelog-{timestamp}.json)\n");
        sb.append("Find more info in the README file\n");
        return sb.toString();
    }
}
