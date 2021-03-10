"""
This script is intended to be used by IntelliJs "External Tools" functionality:
    * Program:      python3
    * Arguments:    "Dev scripts/Run groovy script in insight.py"
    * Working Dir:  $ProjectFileDir$

The script requires python3 and that the re and requests dependencys are installed.
"""

import requests
import re

jiraUrl = "https://jira.domain.se"
jiraRestUser = "user"
jiraRestPw = "password"
filePath = "Examples/Insight Groovy/Wrapper Script.groovy"
verifyJiraCert = False
prettifyOutput = True


insightPath = "/rest/insight/1.0/groovy/evaluate"

scriptText = open(filePath,"r").read()
json = {"code":scriptText, "issueKey": "", "objectKey":""}


request = requests.post(url=jiraUrl+insightPath, json=json, auth=(jiraRestUser, jiraRestPw), verify=verifyJiraCert)

if (request.ok):

    rawLow = request.json()["log"]

    if (prettifyOutput):
        for line in re.finditer("^\d+-\d+-\d+ .*?\|(.*)$",rawLow,re.MULTILINE):
            print(line.group(1))
    else:
        print(rawLow)

else:
    print("There was an error running your code")
    
    print("\tInfo:" + request.json()["infoMessage"])
    print("\tException:" + request.json()["exception"])
    print("\tLog:" + request.json()["log"])