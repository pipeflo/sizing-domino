# sizing-service-domino

This tool shows the basics of what is needed to provide sizing for IBM Domino servers.

Please see the LICENSE file for copyright and license information.

## Getting Started

NOTE: For access to the Cloudant database, please contact me directly.

### Clone this project to your own repo on github.ibm.com, and import it into your Eclipse environment. 

1. Create a repository in github.ibm.com, and clone this project into it.
1. Make sure the Maven plugin is installed in Eclipse.
1. Make sure you can install a Maven project from git. 
[This link](https://stackoverflow.com/questions/4869815/importing-a-maven-project-into-eclipse-from-git) may help.
1. Import the project.
1. Right click on the project, choose Maven -> Update Project.

### Configure Liberty to run the project locally

1. Create a Liberty Server configuration in RAD.
1. Look in `manifest.yml` for the environment variables used by this application (`DOMINO_SIZING_SERVICE_LOG`, etc).
1. Configure your Liberty environment to have these variables. This involves editing the `server.env` file, which
you can do by finding it in the Servers view in RAD and double clicking on it. Edit it so it looks something like this:
```
DOMINO_SIZING_SERVICE_LOG=true
DOMINO_QUESTIONNAIRE_DBNAME=questionnaire
MACHINE_TYPES_DBNAME=machine_types_1
DOMINO_SPREADSHEET_TEMPLATE=IBM_Domino_Server_Workload_Tool_24Aug2017v2_TEMPLATE.xlsx
VCAP_SERVICES=<the VCAP variable from your Bluemix configuration>
```
4. Right click on the server in the Servers view and choose "Add and Remove". Select your application 
from the Available ones on the left and add it to the Configured list on the right.
5. Double click on the "Server Configuration [server.xml]" for this Liberty server. 
6. Click on your Web Application on the left and enter a slash (/) for the Application context root on the right.
7. Save the changes and start the server.
8. In the Console view, you should see a line like this: `Web application available (default_host): http://localhost:9080/`.
Click on the link to open a browser window and test your app.


### Run on Bluemix

1. Go to [https://console.bluemix.net/devops/toolchains](https://console.bluemix.net/devops/toolchains) and create a new Toolchain.
1. Choose "Simple Cloud Foundry toolchain (v2)".
1. Change the Toolchain name to something meaningful to you.
1. Under Tool Integrations, leave the Git Repos and Eclipse Orion settings as they are. 
Click on Delivery Pipeline and change the App name to something unique and meaningful to you.
1. Click the Create button.
1. When it finishes, you should be on the Overview page of your toolchain. Click the Add a Tool button.
1. Add the tool called GitHub Enterprise Whitewater. You will need to click the "I understand" button and allow Bluemix to access your repos on github.ibm.com.
1. For Repository Type, choose Link. Paste in the URL of your repo. Select "Track deployment of code changes"; other settings are optional.
1. Click the Create Integration button.
1. When it completes, you should be back at the Toolchain overview page. Click the Delivery Pipeline tool.
1. On the BUILD step, click on the gear icon and select Configure Stage.
1. Click on the INPUT tab. On the Git repository dropdown, choose the new repo you added in the steps above. Click Save.
1. Back on the overview page, click the Play button on the BUILD stage to test your Toolchain. If the BUILD
stage completes, the DEPLOY stage will begin. If the DEPLOY stage completes successfully, your app will be 
running in Bluemix. Test it out!


