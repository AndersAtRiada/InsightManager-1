package customRiadaLibraries.insightmanager

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.config.util.JiraHome
import com.atlassian.jira.permission.GlobalPermissionKey
import com.atlassian.jira.project.Project
import com.atlassian.jira.project.ProjectManager
import com.atlassian.jira.security.GlobalPermissionManager
import com.atlassian.jira.security.JiraAuthenticationContext
import com.atlassian.jira.security.roles.ProjectRoleManager
import com.atlassian.jira.user.ApplicationUser
import com.atlassian.jira.user.util.UserManager
import com.atlassian.jira.util.BaseUrl
import com.onresolve.scriptrunner.runner.customisers.WithPlugin
import com.riadalabs.jira.plugins.insight.channel.external.api.facade.impl.IQLFacadeImpl
import com.riadalabs.jira.plugins.insight.channel.external.api.facade.impl.InsightPermissionFacadeImpl
import com.riadalabs.jira.plugins.insight.channel.external.api.facade.impl.ObjectSchemaFacadeImpl
import com.riadalabs.jira.plugins.insight.channel.external.api.facade.impl.ProgressFacadeImpl
import com.riadalabs.jira.plugins.insight.common.exception.ImportObjectSchemaException
import com.riadalabs.jira.plugins.insight.services.model.ObjectBean
import com.riadalabs.jira.plugins.insight.services.model.ObjectSchemaBean
import com.riadalabs.jira.plugins.insight.services.progress.ProgressCategory
import com.riadalabs.jira.plugins.insight.services.progress.model.Progress
import com.riadalabs.jira.plugins.insight.services.progress.model.ProgressId
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import io.riada.core.share.model.Share
import jline.internal.InputStreamReader
import org.apache.groovy.json.internal.LazyMap
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.junit.runner.JUnitCore
import org.junit.runner.Result
import spock.config.ConfigurationException
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.concurrent.TimeoutException
import java.util.regex.Matcher
import java.util.regex.Pattern

@WithPlugin("com.riadalabs.jira.plugins.insight")




Logger log = Logger.getLogger("test.report")
log.setLevel(Level.ALL)

/*
SpecHelper specHelper = new SpecHelper()
specHelper.validateAndCacheSettings()
ObjectSchemaBean testSchema = specHelper.setupTestObjectSchema("Test Schema1", "TSS")


log.debug("Deletd?:" + specHelper.deleteScheme(testSchema))


return

 */

/**
 * Manual Steps:
 *  Create a JSD project, use the "Basic" template
 */



JUnitCore jUnitCore = new JUnitCore()

//Result spockResult = jUnitCore.run(Request.method(InsightManagerForScriptRunnerSpecifications.class, 'Test readOnly mode of attachment operations'))
Result spockResult = jUnitCore.run(InsightManagerForScriptRunnerSpecificationsV2)


spockResult.failures.each { log.error(it) }

spockResult.each { log.info("Result:" + it.toString()) }

log.info("Was successful:" + spockResult.wasSuccessful())

/*
SpecHelper specHelper = new SpecHelper()
specHelper.validateAndCacheSettings()
//specHelper.getObjectSchemaRoles(9)
//specHelper.getObjectSchemaRoleActors(9)
specHelper.setSchemaRoleActors(9, "Object Schema Managers", ["jira-administrators"], ["JIRAUSER10301"])

 */

class InsightManagerForScriptRunnerSpecificationsV2 extends Specification {


    /*
    @Shared
    String jiraAdminUserName = "anders"
    @Shared
    String jiraAdminPassword = "anders"

     */


    @Shared
    Class iqlFacadeClass
    @Shared
    IQLFacadeImpl iqlFacade
    @Shared
    ObjectSchemaFacadeImpl objectSchemaFacade
    @Shared
    Class objectSchemaFacadeClass
    @Shared
    ProgressFacadeImpl progressFacade
    @Shared
    Class progressFacadeClass

    @Shared
    UserManager userManager
    @Shared
    JiraAuthenticationContext jiraAuthenticationContext


    @Shared
    Logger log = Logger.getLogger(this.class)
    @Shared
    SpecHelper specHelper = new SpecHelper()

    @Shared
    ObjectSchemaBean testSchema


    def setupSpec() {

        log.setLevel(Level.ALL)
        objectSchemaFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.ObjectSchemaFacade")
        iqlFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.IQLFacade");
        progressFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.ProgressFacade");


        objectSchemaFacade = ComponentAccessor.getOSGiComponentInstanceOfType(objectSchemaFacadeClass) as ObjectSchemaFacadeImpl
        iqlFacade = ComponentAccessor.getOSGiComponentInstanceOfType(iqlFacadeClass) as IQLFacadeImpl
        progressFacade = ComponentAccessor.getOSGiComponentInstanceOfType(progressFacadeClass) as ProgressFacadeImpl

        userManager = ComponentAccessor.getUserManager()
        jiraAuthenticationContext = ComponentAccessor.getJiraAuthenticationContext()

        assert specHelper.validateAndCacheSettings()


        if (specHelper.userRunningTheScript == specHelper.jiraAdminUser) {
            log.warn("The jiraAdmin and the user running this script should ideally not be the same.")
        }
    }

    def cleanupSpec() {
        log.info("Starting cleanup after all tests")

        if (specHelper.deleteScheme(testSchema)) {
            log.debug("\tDeleted test scheme")
            testSchema = null
        } else {
            throw new RuntimeException("Error deleting test scheme:" + testSchema.name)
        }

    }

    def setup() {

        log.info("Starting Setup before feature method")

        if (testSchema == null) {
            log.debug("\tSetting up ObjectScheme for testing")
            setupTestSchema()
            log.debug("\tSetup Scheme with id:" + testSchema.id)
        }

        log.info("\tFinished Setup before feature method")


    }

    boolean setupTestSchema() {
        testSchema = specHelper.setupTestObjectSchema()

    }

    /*
    def cleanup() {
        log.info("Starting cleanup after feature method")

        if (specHelper.deleteScheme(testSchema)) {
                    log.debug("\tDeleted test scheme")
                    testSchema = null
                } else {
                    throw new RuntimeException("Error deleting test scheme:" + testSchema.name)
                }
    }

     */


    def "Verify that setServiceAccount() finds the user regardless of input type"() {


        when: "Testing with username"


        log.info("Testing setServiceAccount() with username")
        InsightManagerForScriptrunner im = new InsightManagerForScriptrunner()
        im.setServiceAccount(specHelper.jiraAdminUser.username)

        then: "Checking that im.initialUser and im.serviceUser was set correctly"

        im.initialUser == specHelper.userRunningTheScript
        im.serviceUser == specHelper.jiraAdminUser

        log.info("\tsetServiceAccount when supplied with a username works as intended")


        when: "Testing with applicationUser"
        log.info("Testing setServiceAccount() with applicationUser")
        im = new InsightManagerForScriptrunner()
        im.setServiceAccount(specHelper.jiraAdminUser)

        then: "Checking that im.initialUser and im.serviceUser was set correctly"

        im.initialUser == specHelper.userRunningTheScript
        im.serviceUser == specHelper.jiraAdminUser

        log.info("\tsetServiceAccount when supplied with a applicationUser works as intended")


    }


    def "Verify IQL searching"(String iql, long matchSize, ApplicationUser loggedInUser, ApplicationUser serviceAccount) {


        setup: "Setting up the Jira Logged in user"
        log.info("Will test IQL searching")
        log.info("\tWill run as logged in user:" + loggedInUser)
        log.info("\tWill run IM with service user:" + serviceAccount)
        log.info("\tWill use IQL:" + iql)
        log.info("\tExpect to find $matchSize objects")
        jiraAuthenticationContext.setLoggedInUser(loggedInUser)


        when: "Setting up IM to use the serviceAccount and executing the IQL"
        InsightManagerForScriptrunner im = new InsightManagerForScriptrunner()
        im.setServiceAccount(serviceAccount)
        ArrayList<ObjectBean> matchingObjects = im.iql(testSchema.id, iql)


        then: "The expected matching number of objects are found"
        matchingObjects.size() == matchSize
        log.debug("\tFound the correct number of objects")

        and: "The currently logged in user is restored"
        jiraAuthenticationContext.loggedInUser == loggedInUser


        cleanup: "Restore the logged in user to the user running the script"
        jiraAuthenticationContext.setLoggedInUser(specHelper.userRunningTheScript)


        where:
        iql                                           | matchSize | loggedInUser                    | serviceAccount
        "ObjectType = \"Object With All Attributes\"" | 2         | specHelper.jiraAdminUser        | specHelper.jiraAdminUser
        "ObjectType = \"Object With All Attributes\"" | 2         | specHelper.insightSchemaManager | specHelper.insightSchemaManager
        "ObjectType = \"Object With All Attributes\"" | 2         | specHelper.insightSchemaUser    | specHelper.insightSchemaUser
        "ObjectType = \"Object With All Attributes\"" | 0         | specHelper.projectCustomer      | specHelper.projectCustomer
        "ObjectType = \"Object With All Attributes\"" | 2         | specHelper.projectCustomer      | specHelper.jiraAdminUser


    }


    def "Test renderObjectToHtml()"(String objectIql, long matchSize, String attributeToRender, expectedAttributeValue, ApplicationUser loggedInUser, ApplicationUser serviceAccount) {

        setup: "Setting up the Jira Logged in user"

        Pattern pattern = Pattern.compile("<table>\\s*<tr>\\s*<td.*?><b>(.*?):<\\/b><\\/td>\\s*?<td.*?>(.*?)<\\/td>\\s*?<\\/tr><\\/table>")

        log.info("Will test IQL searching")
        log.info("\tWill run as logged in user:" + loggedInUser)
        log.info("\tWill run IM with service user:" + serviceAccount)
        log.info("\tWill use this IQL to find one or zero test objects:" + objectIql)
        log.info("\tExpect to find $matchSize objects")
        jiraAuthenticationContext.setLoggedInUser(loggedInUser)

        when: "Getting the Insight test object"
        InsightManagerForScriptrunner im = new InsightManagerForScriptrunner()
        im.setServiceAccount(serviceAccount)
        ArrayList<ObjectBean> testObjects = im.iql(testSchema.id, objectIql)

        then: "The matchSize is not larger than 1"
        matchSize <= 1


        and: "The expected number of objects where found"
        testObjects.size() == matchSize


        when: "Rendering the object as html"
        Matcher matcher
        if (matchSize != 0) {
            String objectHtml = im.renderObjectToHtml(testObjects.first(), [attributeToRender])
            matcher = pattern.matcher(objectHtml)
        }


        then: "HTML table appears to be well formed "
        if (matchSize != 0) {
            matcher.size() == 1
            matcher[0].size() == 3
        }


        and: "Table values are correct"
        if (matchSize != 0) {
            matcher[0][1] == attributeToRender
            matcher[0][2] == expectedAttributeValue
        }


        and: "The currently logged in user is restored"
        jiraAuthenticationContext.loggedInUser == loggedInUser


        cleanup: "Restore the logged in user to the user running the script"
        jiraAuthenticationContext.setLoggedInUser(specHelper.userRunningTheScript)

        where:
        objectIql                      | matchSize | attributeToRender | expectedAttributeValue                                                                          | loggedInUser               | serviceAccount
        "\"Name\" = \"Sample object\"" | 1         | "Name"            | "Sample object"                                                                                 | specHelper.jiraAdminUser   | specHelper.jiraAdminUser
        "\"Name\" = \"Sample object\"" | 1         | "Boolean"         | "true"                                                                                          | specHelper.jiraAdminUser   | specHelper.jiraAdminUser
        "\"Name\" = \"Sample object\"" | 1         | "Integer"         | "1"                                                                                             | specHelper.jiraAdminUser   | specHelper.jiraAdminUser
        "\"Name\" = \"Sample object\"" | 1         | "Text"            | "Text value"                                                                                    | specHelper.jiraAdminUser   | specHelper.jiraAdminUser
        "\"Name\" = \"Sample object\"" | 1         | "Float"           | "3.1415"                                                                                        | specHelper.jiraAdminUser   | specHelper.jiraAdminUser
        "\"Name\" = \"Sample object\"" | 1         | "URL"             | "http://www.altavista.com"                                                                      | specHelper.jiraAdminUser   | specHelper.jiraAdminUser
        "\"Name\" = \"Sample object\"" | 1         | "Email"           | "nisse@hult.com"                                                                                | specHelper.jiraAdminUser   | specHelper.jiraAdminUser
        "\"Name\" = \"Sample object\"" | 1         | "Textarea"        | "<p>Some</p><p>text</p><p>on</p><p>many</p><p>lines</p><p><strong>with formating </strong></p>" | specHelper.jiraAdminUser   | specHelper.jiraAdminUser
        "\"Name\" = \"Sample object\"" | 1         | "Select"          | "The First Option,The Second Option,The Third Option"                                           | specHelper.jiraAdminUser   | specHelper.jiraAdminUser
        "\"Name\" = \"Sample object\"" | 1         | "IP Address"      | "127.0.0.1"                                                                                     | specHelper.jiraAdminUser   | specHelper.jiraAdminUser
        "\"Name\" = \"Sample object\"" | 1         | "Group"           | "jira-administrators"                                                                           | specHelper.jiraAdminUser   | specHelper.jiraAdminUser
        "\"Name\" = \"Sample object\"" | 1         | "Status"          | "Active"                                                                                        | specHelper.jiraAdminUser   | specHelper.jiraAdminUser
        "\"Name\" = \"Sample object\"" | 1         | "Date"            | "Wed Sep 16 00:00:00 UTC 2020"                                                                  | specHelper.jiraAdminUser   | specHelper.jiraAdminUser
        "\"Name\" = \"Sample object\"" | 1         | "DateTime"        | "Sun Sep 13 16:49:26 UTC 2020"                                                                  | specHelper.jiraAdminUser   | specHelper.jiraAdminUser
        "\"Name\" = \"Sample object\"" | 0         | ""                | ""                                                                                              | specHelper.projectCustomer | specHelper.projectCustomer
        "\"Name\" = \"Sample object\"" | 1         | "Email"           | "nisse@hult.com"                                                                                | specHelper.projectCustomer | specHelper.jiraAdminUser


    }


}


class SpecHelper {

    Logger log = Logger.getLogger(this.class)
    Class iqlFacadeClass
    IQLFacadeImpl iqlFacade
    ObjectSchemaFacadeImpl objectSchemaFacade
    Class objectSchemaFacadeClass
    Class insightPermissionFacadeClass
    InsightPermissionFacadeImpl insightPermissionFacade
    ProgressFacadeImpl progressFacade
    Class progressFacadeClass


    UserManager userManager = ComponentAccessor.getUserManager()
    ProjectManager projectManager = ComponentAccessor.getProjectManager()
    ProjectRoleManager projectRoleManager = ComponentAccessor.getComponentOfType(ProjectRoleManager)
    GlobalPermissionManager globalPermissionManager = ComponentAccessor.getGlobalPermissionManager()


    File jiraHome = ComponentAccessor.getComponentOfType(JiraHome).getHome()
    String jiraBaseUrl = ComponentAccessor.getComponentOfType(BaseUrl).getCanonicalBaseUrl()

    File settingsFile = new File(System.getProperty("java.io.tmpdir") + "/" + super.class.simpleName + "/settings.json")
    Map settings


    ApplicationUser jiraAdminUser
    String jiraAdminPassword
    ApplicationUser userRunningTheScript
    ApplicationUser insightSchemaUser
    ApplicationUser insightSchemaManager
    ApplicationUser projectAdmin
    ApplicationUser projectCustomer

    Project jsdProject
    //ObjectSchemaBean objectSchemaBean

    SpecHelper() {
        this.log.setLevel(Level.ALL)

        objectSchemaFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.ObjectSchemaFacade")
        iqlFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.IQLFacade");
        insightPermissionFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.InsightPermissionFacade");
        progressFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.ProgressFacade");


        objectSchemaFacade = ComponentAccessor.getOSGiComponentInstanceOfType(objectSchemaFacadeClass) as ObjectSchemaFacadeImpl
        iqlFacade = ComponentAccessor.getOSGiComponentInstanceOfType(iqlFacadeClass) as IQLFacadeImpl
        insightPermissionFacade = ComponentAccessor.getOSGiComponentInstanceOfType(insightPermissionFacadeClass) as InsightPermissionFacadeImpl
        progressFacade = ComponentAccessor.getOSGiComponentInstanceOfType(progressFacadeClass) as ProgressFacadeImpl

        userRunningTheScript = ComponentAccessor.getJiraAuthenticationContext().loggedInUser


        Map defaultSettings = [

                jiraGlobal: [
                        adminUsername: "admin",
                        adminPassword: "password"
                ],
                insight   : [
                        schemaManager: "UserName",
                        schemaUser   : "SchemaUser"
                ],
                jsdProject: [
                        key            : "KEY",
                        projectAdmin   : "UserName",
                        projectCustomer: "CustomerName"
                ]
        ]
        boolean fileCreated = settingsFile.createNewFile()

        if (fileCreated) {


            settingsFile.write(JsonOutput.prettyPrint(JsonOutput.toJson(defaultSettings)))
            log.info("A new settings file was created:" + settingsFile.path)
            settings = new JsonSlurper().parse(settingsFile) as Map

            log.info("Will now stop execution to allow user setup of settingsFile:" + settingsFile.canonicalPath)
            throw new ConfigurationException("Setup the newly created settingsFile:" + settingsFile.canonicalPath)
        } else {
            settings = new JsonSlurper().parse(settingsFile) as Map

            if (settings == null || settings.isEmpty()) {
                throw new InputMismatchException("Could not parse settings file")
            }

            if (!settings.keySet().containsAll(defaultSettings.keySet())) {

                throw new InputMismatchException("Settings file is missing keys, it should contain:" + defaultSettings.keySet())
            }

        }


    }


    boolean validateAndCacheSettings() {

        log.info("Checking if settings are valid")
        log.info("Settings file:" + settingsFile.canonicalPath)
        log.info("The settings are:")
        settings.toPrettyJsonString().eachLine { log.info("\t" + it) }

        //Check the supplied project
        jsdProject = projectManager.getProjectByCurrentKey(settings.jsdProject.key)
        assert jsdProject != null: "Could not find project with key: " + settings.jsdProject.key
        assert jsdProject.projectTypeKey.key == "service_desk": "The supplied project is not a service desk project."

        //Check the supplied projectAdmin
        projectAdmin = userManager.getUserByName(settings.jsdProject.projectAdmin)
        assert projectAdmin != "Could not find the supplied project admin: " + settings.jsdProject.projectAdmin
        assert projectRoleManager.isUserInProjectRole(projectAdmin, projectRoleManager.getProjectRole("Administrators"), jsdProject): "The supplied project admin ($projectAdmin) is not admin of the project" + jsdProject.name

        //Chech the supplied JSD Customer
        projectCustomer = userManager.getUserByName(settings.jsdProject.projectCustomer)
        assert projectRoleManager.getProjectRoles(projectCustomer, jsdProject).isEmpty(): "The JSD customer should not have any project roles in the JSD project:" + projectCustomer


        //Check the supplied JIRA admin
        jiraAdminUser = userManager.getUserByName(settings.jiraGlobal.adminUsername)
        jiraAdminPassword = settings.jiraGlobal.adminPassword
        assert jiraAdminUser != null: "Could not find adminUser:" + settings.jiraGlobal.adminUsername
        assert jiraAdminPassword != null: "Jira adminPassword not supplied"
        assert insightPermissionFacade.hasAdminPermission(jiraAdminUser): "The jiraGlobal.adminUsername is not Insight Admin"
        assert globalPermissionManager.hasPermission(GlobalPermissionKey.ADMINISTER, jiraAdminUser): "The supplied JIRA global admin, is not admin"

        //Check the supplied Insight Schema
        //objectSchemaBean = objectSchemaFacade.loadObjectSchema(settings.insight.objectSchemaId)
        //assert objectSchemaBean != null: "Could not find ObjectSchema with ID:" + settings.insight.objectSchemaId

        //Check the supplied schema manager
        insightSchemaManager = userManager.getUserByName(settings.insight.schemaManager)
        assert insightSchemaManager != null: "Could not find schema manager:" + settings.insight.schemaManager
        //assert insightPermissionFacade.hasInsightSchemaManagerPermission(insightSchemaManager, objectSchemaBean.id): "The insight.schemaManager should have Admin permissions in Insight scheme " + objectSchemaBean.name

        //Check the supplied schemaUser
        insightSchemaUser = userManager.getUserByName(settings.insight.schemaUser)
        assert insightSchemaUser != null: "Could not find schema user:" + settings.insight.schemaUser
        assert !insightPermissionFacade.hasAdminPermission(insightSchemaUser): "The insight.schemaUser should not have Admin permissions in Insight"
        //assert insightPermissionFacade.hasInsightObjectSchemaViewPermission(insightSchemaUser, objectSchemaBean.id): "The insight.schemaUser should have user permissions in Insight scheme " + objectSchemaBean.name


        log.info("The settings file appears valid and has been cached")

        return true

    }

    File createNewGoldenSchemaImage(int schemaId, String zipFileName = "SPOC-golden-image.zip") {

        log.info("Creating new Golden Schema Image")

        File exportedFile = exportScheme(zipFileName, schemaId, true)

        assert exportedFile.exists(): "Failed to create export file:" + exportedFile.path
        assert exportedFile.size() > 0: "Failed to create export file:" + exportedFile.path

        log.info("\tThe image was created successfully and placed here:" + exportedFile.canonicalPath)
        log.info("\tNow manually upload it to:" + "https://github.com/Riada-AB/InsightManager-TestingResources/blob/master/SPOC-golden-image.zip")


        return exportedFile

    }

    ObjectSchemaBean setupTestObjectSchema(String schemaName = "SPOC Testing of IM", String schemaKey = "SPIM", boolean useCachedGoldenImage = true) {

        String imageUrl = "https://github.com/Riada-AB/InsightManager-TestingResources/raw/master/SPOC-golden-image.zip"

        log.info("Setting up new Test ObjectSchema")

        File imageZip = new File(jiraHome.canonicalPath + "/import/insight/" + imageUrl.split("/").last())
        if (useCachedGoldenImage && imageZip.exists()) {
            log.debug("\tUsing cached Golden image")
        } else {
            log.info("Download Schema zip from:" + imageUrl)


            File destinationFolder = new File(System.getProperty("java.io.tmpdir") + "/" + super.class.simpleName)
            destinationFolder.mkdirs()

            log.info("Downloading to:" + destinationFolder.canonicalPath)

            imageZip = downloadFile(imageUrl, destinationFolder.canonicalPath, jiraAdminUser.username, jiraAdminPassword)

            log.info("Download complete, moving Insight import folder")
            imageZip = moveFile(imageZip.canonicalPath, jiraHome.canonicalPath + "/import/insight/" + imageZip.name)
            log.debug("\tFile moved to " + imageZip.canonicalPath)
        }


        log.info("Importing ZIP in to the new schema \"$schemaName\" ($schemaKey)")
        ObjectSchemaBean newSchema = importScheme(imageZip.name, schemaName, schemaKey)

        log.info("The new schema got id:" + newSchema.id)

        log.info("Setting Schema managers to:" + insightSchemaManager)
        log.info("Setting Schema Users to:" + insightSchemaUser)
        setSchemaRoleActors(newSchema.id, "Object Schema Managers", [] as ArrayList, [insightSchemaManager.key] as ArrayList)
        setSchemaRoleActors(newSchema.id, "Object Schema Users", [] as ArrayList, [insightSchemaUser.key] as ArrayList)

        log.info("Finished setting up test schema")
        return newSchema
    }


    File downloadFile(String url, String destinationFolder, String username = "", String password = "") {

        HttpURLConnection connection = setupConnection(url, username, password)


        try {

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {

                String fileName = "";
                String disposition = connection.getHeaderField("Content-Disposition");
                String contentType = connection.getContentType();
                int contentLength = connection.getContentLength();

                if (disposition != null) {
                    // extracts file name from header field
                    int index = disposition.indexOf("filename=");
                    if (index > 0) {
                        fileName = disposition.substring(index + 10,
                                disposition.length() - 1);
                    }
                } else {
                    // extracts file name from URL
                    fileName = url.substring(url.lastIndexOf("/") + 1,
                            url.length());
                }

                log.debug("Content-Type = " + contentType);
                log.debug("Content-Disposition = " + disposition);
                log.debug("Content-Length = " + contentLength);
                log.debug("fileName = " + fileName);

                // opens input stream from the HTTP connection
                InputStream inputStream = connection.getInputStream();
                String saveFilePath = destinationFolder + File.separator + fileName;

                // opens an output stream to save into file
                FileOutputStream outputStream = new FileOutputStream(saveFilePath);

                int bytesRead = -1;
                byte[] buffer = new byte[4096];
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                outputStream.close();
                inputStream.close();

                File downloadedFile = new File(saveFilePath)

                if (downloadedFile.size() > 0) {
                    log.debug("File downloaded successfully: " + downloadedFile.canonicalPath)
                    return downloadedFile
                } else {
                    log.error("Failed to download file:" + url)
                    return null
                }


            } else {
                throw new ConnectException("Unsupported response (" + connection.responseCode + "(${connection.responseMessage})) from " + url)
            }
        } catch (all) {

            log.error("There was an error in the exportScheme method")
            log.error("\tUrl:" + connection.URL.toString())
            if (connection != null) {
                log.error("\tResponse code:" + connection.responseCode)
                log.error("\tResponse Message:" + connection.responseMessage)
                log.error("\tErrorStream:" + readErrorStream(connection))
            }
            log.error("\tException:" + all.message)

            throw all


        }


    }


    static HttpURLConnection setupConnection(String url, String username = "", String password = "") {

        HttpURLConnection connection = new URL(url).openConnection() as HttpURLConnection

        if (username != "" && password != "") {
            String auth = username + ":" + password
            auth = "Basic " + auth.bytes.encodeBase64().toString()

            connection.setRequestProperty("Authorization", auth)
        }


        return connection

    }

    LazyMap httpPostJson(LazyMap json, String url, String username = "", String password = "") {

        HttpURLConnection connection = setupConnection(url, username, password)


        try {
            connection.setDoOutput(true)
            connection.setRequestMethod("POST")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            byte[] jsonByte = new JsonBuilder(json).toPrettyString().getBytes("UTF-8")

            connection.outputStream.write(jsonByte, 0, jsonByte.length)

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {

                def rawReturn = new JsonSlurper().parse(connection.getInputStream())
                LazyMap outJson
                if (rawReturn instanceof ArrayList) {
                    outJson = ["data": rawReturn] as LazyMap
                } else {
                    outJson = rawReturn as LazyMap
                }


                return outJson

            } else {
                throw new ConnectException("Unsupported response (" + connection.responseCode + "(${connection.responseMessage})) from " + url)
            }
        } catch (all) {

            log.error("There was an error in the exportScheme method")
            log.error("\tUrl:" + connection.URL.toString())
            if (connection != null) {
                log.error("\tResponse code:" + connection.responseCode)
                log.error("\tResponse Message:" + connection.responseMessage)
                log.error("\tErrorStream:" + readErrorStream(connection))
            }
            log.error("\tException:" + all.message)

            throw all


        }
    }


    LazyMap httpPutJson(LazyMap json, String url, String username = "", String password = "") {

        HttpURLConnection connection = setupConnection(url, username, password)


        try {
            connection.setDoOutput(true)
            connection.setRequestMethod("PUT")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            byte[] jsonByte = new JsonBuilder(json).toPrettyString().getBytes("UTF-8")

            connection.outputStream.write(jsonByte, 0, jsonByte.length)

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {

                def rawReturn = new JsonSlurper().parse(connection.getInputStream())
                LazyMap outJson
                if (rawReturn instanceof ArrayList) {
                    outJson = ["data": rawReturn] as LazyMap
                } else {
                    outJson = rawReturn as LazyMap
                }


                return outJson

            } else {
                throw new ConnectException("Unsupported response (" + connection.responseCode + "(${connection.responseMessage})) from " + url)
            }
        } catch (all) {

            log.error("There was an error in the exportScheme method")
            log.error("\tUrl:" + connection.URL.toString())
            if (connection != null) {
                log.error("\tResponse code:" + connection.responseCode)
                log.error("\tResponse Message:" + connection.responseMessage)
                log.error("\tErrorStream:" + readErrorStream(connection))
            }
            log.error("\tException:" + all.message)

            throw all


        }
    }

    LazyMap httpGetJson(String url, String username = "", String password = "") {


        HttpURLConnection connection = setupConnection(url, username, password)
        try {

            connection.setRequestMethod("GET")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {

                LazyMap json = new JsonSlurper().parse(connection.getInputStream()) as LazyMap

                return json

            } else {
                throw new ConnectException("Unsupported response (" + connection.responseCode + "(${connection.responseMessage})) from " + url)
            }
        } catch (all) {

            log.error("There was an error in the sendGetJson method")
            log.error("\tUrl:" + url)
            if (connection != null) {
                log.error("\tResponse code:" + connection.responseCode)
                log.error("\tResponse Message:" + connection.responseMessage)
                log.error("\tErrorStream:" + readErrorStream(connection))
            }
            log.error("\tException:" + all.message)

            throw all


        }
    }


    //File is expected to be placed in $JIRAHOME/import/insight
    ObjectSchemaBean importScheme(String fileName, String objectSchemaName, String objectSchemaKey, String objectSchemaDescription = "", boolean includeObjects = true, boolean importAttachments = true, boolean importObjectAvatars = true) {

        log.info("Import Scheme")
        log.info("\tFilename:" + fileName)
        log.info("\tSchema Name:" + objectSchemaName)

        LazyMap inputJson = [
                fileName               : fileName,
                objectSchemaName       : objectSchemaName,
                objectSchemaKey        : objectSchemaKey,
                objectSchemaDescription: objectSchemaDescription,
                includeObjects         : includeObjects,
                importAttachments      : importAttachments,
                importObjectAvatars    : importObjectAvatars
        ]

        log.debug("\tSending JSON to Insight:")
        inputJson.each {
            log.debug("\t\t" + it.key + ":" + it.value)
        }

        LazyMap result = httpPostJson(inputJson, jiraBaseUrl + "/rest/insight/1.0/objectschemaimport/import/server", jiraAdminUser.username, jiraAdminPassword)

        log.debug("\tGot response JSON from Insight:")
        result.each {
            log.debug("\t\t" + it.key + ":" + it.value)
        }

        Progress importProgress = progressFacade.getProgress(new ProgressId(result.resourceId as String, ProgressCategory.IMPORT_OBJECT_SCHEMA))
        log.debug("\tWaiting for import to finish")
        progressFacade.waitForProgressToComplete(importProgress)

        if (importProgress.error) {
            log.error("There was an error importing the Schema")
            log.error("Import message:" + importProgress.resultMessage)
            log.error("Import status:" + importProgress.status.toString())
            throw new InputMismatchException("There was an error importing the Schema" + importProgress.resultMessage)
        } else {
            log.debug("\tImport has finished")
        }

        return objectSchemaFacade.loadObjectSchema(result.resultData.objectSchemaId)

    }


    //File is placed in $JIRAHOME/export/insight
    File exportScheme(String fileName, int SchemaId, boolean includeObjects) {

        long maxWaitForExportMs = 60000
        log.info("Exporting Scheme")
        log.info("\tFilename:" + fileName)
        log.info("\tSchema ID" + SchemaId)
        log.info("\tWill include objects:" + includeObjects)


        ObjectSchemaBean objectSchema = objectSchemaFacade.loadObjectSchema(SchemaId)
        int totalObjectsInExport = iqlFacade.findObjects(objectSchema.id, "ObjectSchemaId = " + objectSchema.id).size()

        LazyMap json = [
                fileName            : fileName,
                objectSchemaId      : objectSchema.id,
                includeObjects      : includeObjects,
                objectSchemaName    : objectSchema.name,
                totalObjectsInExport: totalObjectsInExport

        ]

        log.debug("Sending JSON to Insight:")
        json.each {
            log.debug(it.key + ":" + it.value)
        }

        LazyMap result = httpPostJson(json, this.jiraBaseUrl + "/rest/insight/1.0/objectschemaexport/export/server", jiraAdminUser.username, jiraAdminPassword)

        log.debug("Got response JSON from Insight:")
        result.each {
            log.debug(it.key + ":" + it.value)
        }

        String actualFileName = result.resultData.exportFileName

        log.info("The exported file will be named:" + actualFileName)


        Instant startOfImport = new Date().toInstant()
        File exportedFile = new File(jiraHome.path + "/export/insight/" + actualFileName)
        long fileSize = 0

        while ((new Date().toInstant().toEpochMilli() - startOfImport.toEpochMilli()) <= maxWaitForExportMs) {

            if (!exportedFile.exists() || exportedFile.size() == 0) {
                sleep(100)
            } else if (fileSize == exportedFile.size()) {

                return exportedFile
            } else {

                fileSize = exportedFile.size()
                sleep(100)

            }


        }

        throw new TimeoutException("Timed out waiting for export to finish, gave up after:" + (new Date().toInstant().toEpochMilli() - startOfImport.toEpochMilli()) + " ms")


    }

    boolean deleteScheme(ObjectSchemaBean objectSchemaBean) {

        objectSchemaFacade.deleteObjectSchemaBean(objectSchemaBean.id)

        if (objectSchemaFacade.loadObjectSchema(objectSchemaBean.id) == null) {
            return true
        } else {
            throw new InputMismatchException("Failed to delete ObjectScheme: " + objectSchemaBean.name + " (${objectSchemaBean.id})")
        }

    }


    Map setSchemaRoleActors(long schemaId, String roleName, ArrayList<String> groupNames, ArrayList<String> userKeys) {


        Integer roleId = getObjectSchemaRoles(schemaId).find { it.key == roleName }.value
        Map categorisedActors = [
                "atlassian-user-role-actor" : userKeys,
                "atlassian-group-role-actor": groupNames

        ]


        LazyMap postJson = [
                id               : roleId,
                categorisedActors: categorisedActors
        ]

        LazyMap newRoleActors = httpPutJson(postJson, this.jiraBaseUrl + "/rest/insight/1.0/config/role/" + roleId, jiraAdminUser.username, jiraAdminPassword)

        assert newRoleActors.name == roleName: "Error setting schema role actors for schema $schemaId"
        assert newRoleActors.actors.name.containsAll(userKeys)

        return newRoleActors

    }

    Map getObjectSchemaRoleActors(long schemaId) {

        Map schemaRoles = getObjectSchemaRoles(schemaId)
        Map returnMap = [:]
        schemaRoles.each { roleName, roleId ->

            LazyMap actorResult = httpGetJson(this.jiraBaseUrl + "/rest/insight/1.0/config/role/" + roleId, jiraAdminUser.username, jiraAdminPassword)


            assert actorResult.id == roleId: "Error retrieving actors for role $roleName in schema $schemaId"

            returnMap.put(roleName, [
                    id                 : roleId,
                    groupRoleActorNames: actorResult.actors.findAll { it.type == "atlassian-group-role-actor" }.name,
                    userRoleActorKeys  : actorResult.actors.findAll { it.type == "atlassian-user-role-actor" }.name
            ])

        }

        log.debug(returnMap.toPrettyJsonString())
        return returnMap
    }


    Map<String, Integer> getObjectSchemaRoles(long schemaId) {

        LazyMap httpResult = httpGetJson(this.jiraBaseUrl + "/rest/insight/1.0/config/role/objectschema/" + schemaId, jiraAdminUser.username, jiraAdminPassword)

        Map returnMap = [:]

        httpResult.each {
            returnMap.put(it.key, it.value.find("\\d+\$") as Integer)
        }

        return returnMap as Map //[Object Schema Managers:62, Object Schema Developers:63, Object Schema Users:64]
    }


    static File moveFile(String source, String destination) {

        Path sourcePath = new File(source).toPath()
        Path destinationPath = new File(destination).toPath()
        return Files.move(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING).toFile()
    }

    private static String readErrorStream(HttpURLConnection connection) {

        String returnString = ""
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))
        bufferedReader.eachLine {
            returnString += it
        }

        return returnString

    }
}

