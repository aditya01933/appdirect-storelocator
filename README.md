# AppDirect Store Locator

An example Play Framework application that uses two-legged OAuth (v1)
and OpenID for SSO to integrate with AppDirect's platform. By default it
uses an H2 in-memory database for "persistence" and Slick for
interfacing with the database.

To run the app five possibilities are provided:

 * As a "native" Play application locally.
 * As a "native" Play application inside a Docker container.
 * As a "native" Play application inside a VM.
 * As a Tomcat webapp inside a VM.
 * As a "native" Play application in Google Cloud (inside a Docker container).

## Getting started

NOTE: The application requires that Java is installed.

To run the tests:

    $ ./activator test

To start the application:

    $ ./activator run

See below for other ways to run the application.

## Configure the application

The default settings for OAuth and OpenID as well as Play's own settings
can be found in the file `conf/application.conf`.  Create your local
configuration file in `conf/application.local.conf` by copying the
sample:

    $ cp conf/application.local.conf.sample conf/application.local.conf

The only need to change the OAuth consumer key and secret which can be
found under the "Edit Integration Settings" panel in the "OAuth
configuration" section of your app.

```
## OAuth
# See https://www.appdirect.com/cms/editApp/<app-id>#edit-integration
# for these values
appdirect.oauth-consumer-key = "store-locator-XXX"
appdirect.oauth-consumer-secret = "XXXXXXXXXXXXXXXX"

## OpenID
# Only override the realm if you need it to work on multiple subdomains.
#
# appdirect.openid-url = "https://www.appdirect.com/openid/id"
# appdirect.openid-realm = "http://*.priorarts.org"

## Database
# Automatically run the SQL scripts to create all tables
applyEvolutions.default=true
# db.default.driver=org.h2.Driver
# db.default.url="jdbc:h2:mem:play;DB_CLOSE_DELAY=-1"
# db.default.user=sa
# db.default.password=""
```

## Running the application inside Docker

These steps will create a Docker image and start a container:

    $ ./activator docker:publishLocal
    $ docker run -p 9000:9001 appdirect-storelocator:1.0-SNAPSHOT
    $ open http://localhost:9001

## Deploying inside a Vagrant VM

Follow these steps to launch a virtual machine with Ubuntu 14.04
(requires that Vagrant and VirtualBox are installed on the host machine)
and run the application:

    host> vagrant up
    # Then ssh into the vm
    host> vagrant ssh
    # Once inside the vm shell execute the install script
    vm:~> sudo /vagrant/install-deps
    # Clone the git repo on the host
    vm:~> git clone /vagrant storelocator
    vm:~> cd storelocator
    # Copy the local configuration, if you created one.
    vm:~/storelocator> cp /vagrant/conf/application.local.conf conf/
    # Build the application (take a coffee break while it downloads deps)
    vm:~/storelocator> ./activator compile

### Running as Play app

The following commands will run the Play application in dev mode:

    vm:~/storelocator> ./activator run
    # Open in your browser on the host machine
    host> open http://localhost:9001

### Running as Tomcat webapp

To running it as a Tomcat webapp, you must first install Tomcat and then
build and deploy the WAR file:

    vm:~/storelocator> sudo apt-get install tomcat7
    # Remove default ROOT webapp
    vm:~/storelocator> sudo rm -rf /var/lib/tomcat7/webapps/ROOT/
    # Then build the WAR file and deploy it:
    vm:~/storelocator> ./activator war
    vm:~/storelocator> cp target/appdirect-storelocator*.war /var/lib/tomcat7/webapps/ROOT.war
    # Optionally, view log files
    vm:~/storelocator> sudo tail -f /var/log/tomcat7/catalina.out
    # Open in your browser on the host machine
    host> open http://localhost:8081

## Deploying to Google Cloud

You can use the `gcloud-deploy` script to deploy to Google Cloud. It
requires that you already have the `gcloud` tool(s) installed including
the container preview package. In addition, you should already have a
container cluster configured with an accompanying personal Docker
registry.

The script expects to reads several settings from `conf/gcloud.conf`.
You can create this file by copying the sample:

    $ cp conf/gcloud.conf.sample conf/gcloud.conf

The main settings to change are:

```
## Google Cloud properties
# See https://console.developers.google.com/project
GCLOUD_PROJECT=store-locator-XXXXX
GCLOUD_ZONE=us-central1-b
GCLOUD_NODE=k8s-cluster-1-node-1
GCLOUD_CLUSTER=cluster-1
```

Supported commands are:

    $ gcloud-deploy build	# Build and tag local Docker image
    $ gcloud-deploy push	# Push Docker image to registry
    $ gcloud-deploy reload	# Reload instance to use latest image
    $ gcloud-deploy log		# View app log
    $ gcloud-deploy all		# Execute all the above commands

## Licensing

This program is licensed under the Apache License, version 2. See [the
LICENSE file](LICENSE) for details.
