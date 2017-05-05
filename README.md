sparklib - client interface to the Spark Lighter service
========================================================

 This android library helps you to connect to [senseable's](http://senseable.eu)
Spark Bluetooth Lighter. Cigarette events are stored locally and you can connect
to exactly one Lighter at a time. The Spark companion App needs to be installed
for this library to be used. When including this library in your App, the user
will be presented with a prompt to install the companion App.

# Installation

 For gradle builds you can use npm to resolve the dependency by adding a

    repositories { jcenter() }
    dependencies { compile 'eu.senseable:sparklib:1.2.1'  }

 to the build.gradle file of your application.

# Usage

 The interface is fully defined in the [Spark](https://senseable.github.io/sparklib/eu/senseable/sparklib/Spark.html) class. You can access the API docs [here](https://senseable.github.io/sparklib/). It's simplest to implement the provided callbacks, which get called whenever something is changing and once at registration time. For example to access the currently stored events on the device, you could do something like this:
 
 
    public void onCreate() {
      mSpark = new Spark(this, mSparkCalls);
    }
    
    private Spark.Callbacks mSparkCalls = new Spark.Callbacks.Stub() {
      public void onEventsChanged(List<Spark.Event> events) {
         if (events.size() == 0)
           return;
           
         /** At this point you can access the list of events stored on the SmartPhone, 
           * this function gets called when new events were detected from the lighter,
           * and once shortly after the Spark object was created. 
           */
           
         Date first = events.get(0).beg;
         Log.d(TAG, String.format("got %d new events, starting at %s", events.size(), first.toString()));
      }
    }
