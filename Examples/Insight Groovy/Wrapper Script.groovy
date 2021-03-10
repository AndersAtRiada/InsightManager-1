/**
 * This is a wrapper script, intended to be executed by Insight (Console, automation etc)
 * The script will setup a new Groovy-environment that can load classes from $JIRA_HOME/scripts (just like ScriptRunner)
 * Inside the new environment the wrapper script will then execute your script which can now load the InsightManagerForInsight class
 *
 * The only thing you really need to change is scriptName
 *  This needs to point to the script you´d really like to execute and must include any sub-directories to $JIRA_HOME/scripts eg:
 *      ReloadingTest.groovy
 *      mySubDir/mySecondSubDir/ReloadingTest.groovy
 *
 */


import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.config.util.JiraHome





String scriptName = "ReloadingTest.groovy"

//Setup the root directory load classes from, $JIRA_HOME/scripts
File scriptDir = new File(ComponentAccessor.getComponent(JiraHome.class).homePath + "/scripts")

//This part prepares to pass the standard variables log/issue/object from the original groovy environment to the new env.
Binding binding = new Binding()
if (this.binding.hasVariable("log")){
    binding.setVariable("log",log)
}
if (this.binding.hasVariable("issue")){
    binding.setVariable("issue",issue)
}
if (this.binding.hasVariable("object")){
    binding.setVariable("object",object)
}


GroovyScriptEngine engine = new GroovyScriptEngine([scriptDir.toURI().toURL()] as URL[], super.getClass().getClassLoader().parent)
engine.run(scriptName, binding)

