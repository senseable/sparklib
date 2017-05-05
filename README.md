sparklib - client interface to the Spark Lighter service
========================================================

 This android library helps you to connect to [senseable's](https://www.senseable.eu)
Spark Bluetooth Lighter. Cigarette events are stored locally and you can connect
to exactly one Lighter at a time. The Spark companion App needs to be installed
for this library to be used. When including this library in your App, the user
will be presented with a prompt to install the companion App.

# Installation

 For gradle builds you can use npm to resolve the dependency by adding a

    dependencies {
        compile 'eu.senseable:sparklib:1.0.1'
    }

 to the build.gradle file of your application.

# Usage

 The interface is fully defined in the `Spark` class...
