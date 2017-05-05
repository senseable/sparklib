// ISparkDiscoverCallback.aidl
package eu.senseable.service;

// Declare any non-default types here with import statements

interface ISparkCallback {
    void onNewSpark(String addr);
    void onBrownout(boolean batEmpty);
    void onEventsChanged(inout List<String> events);
    void onStatusChanged(int status);
}
